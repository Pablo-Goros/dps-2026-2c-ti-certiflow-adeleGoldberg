package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory repositories of the core are the executable specification of every query. This
 * test loads the same data into the real database and checks that each query answers the same
 * thing, in the same order, with aggregates that rebuild to exactly the same state.
 */
class RepositoryParityIT {

    private final StateCodec codec = new StateCodec();
    private Scenario scenario;
    private JdbcPersistence database;

    @BeforeEach
    void setUp() {
        scenario = new Scenario();
        database = TestDatabase.create();
        var system = scenario.system;
        system.catalogue.parties.findAll().forEach(database.parties()::save);
        system.catalogue.assets.findAll().forEach(database.assets()::save);
        system.schemas.findAll().forEach(database.schemas()::save);
        system.inspections.findAll().forEach(database.inspections()::save);
        system.findings.findAll().forEach(database.findings()::save);
        system.certificates.findAll().forEach(database.certificates()::save);
        system.auditTrail.all().forEach(database.auditTrail()::append);
    }

    @Test
    @DisplayName("parties, assets and schemas are listed and searched like the in-memory ones")
    void catalogueAndSchemas() {
        var memory = scenario.system;
        sameAggregates(memory.catalogue.parties.findAll(), database.parties().findAll());
        for (Party party : memory.catalogue.parties.findAll()) {
            sameOptional(memory.catalogue.parties.findById(party.id()), database.parties().findById(party.id()));
        }

        sameAggregates(memory.catalogue.assets.findAll(), database.assets().findAll());
        for (AssetType type : AssetType.values()) {
            sameAggregates(memory.catalogue.assets.findByType(type), database.assets().findByType(type));
            sameOptional(memory.schemas.findByApplicableAssetType(type),
                    database.schemas().findByApplicableAssetType(type));
        }
        for (Party party : memory.catalogue.parties.findAll()) {
            sameAggregates(memory.catalogue.assets.findByResponsible(party.id()),
                    database.assets().findByResponsible(party.id()));
        }
        for (String fragment : List.of("Laboratory", "Laboratory B", "B", "Central", "nothing like it", "", "laboratory")) {
            sameAggregates(memory.catalogue.assets.findByNameContaining(fragment),
                    database.assets().findByNameContaining(fragment));
        }
        sameAggregates(memory.schemas.findAll(), database.schemas().findAll());
        for (InspectionSchema schema : memory.schemas.findAll()) {
            sameOptional(memory.schemas.findById(schema.id()), database.schemas().findById(schema.id()));
        }
    }

    @Test
    @DisplayName("inspections and findings answer every query like the in-memory ones")
    void inspectionsAndFindings() {
        var memory = scenario.system;
        sameAggregates(memory.inspections.findAll(), database.inspections().findAll());
        for (Inspection inspection : memory.inspections.findAll()) {
            sameOptional(memory.inspections.findById(inspection.id()), database.inspections().findById(inspection.id()));
            assertThat(database.inspections().summaryOf(inspection.id()))
                    .isEqualTo(memory.inspections.summaryOf(inspection.id()));
            assertThat(database.inspections().wasRectified(inspection.id()))
                    .isEqualTo(memory.inspections.wasRectified(inspection.id()));
        }
        for (Asset asset : memory.catalogue.assets.findAll()) {
            sameOptional(memory.inspections.findNonClosedByAsset(asset.id()),
                    database.inspections().findNonClosedByAsset(asset.id()));
            sameAggregates(memory.inspections.findClosedByAsset(asset.id()),
                    database.inspections().findClosedByAsset(asset.id()));
        }

        sameAggregates(memory.findings.findAll(), database.findings().findAll());
        sameAggregates(memory.findings.findWithOpenActions(), database.findings().findWithOpenActions());
        assertThat(database.findings().findWithOpenActions()).isNotEmpty();
        for (Finding finding : memory.findings.findAll()) {
            sameOptional(memory.findings.findById(finding.id()), database.findings().findById(finding.id()));
            sameOptional(memory.findings.findByCriterion(finding.inspectionId(), finding.criterionId()),
                    database.findings().findByCriterion(finding.inspectionId(), finding.criterionId()));
        }
        for (Inspection inspection : memory.inspections.findAll()) {
            sameAggregates(memory.findings.findByInspection(inspection.id()),
                    database.findings().findByInspection(inspection.id()));
        }
    }

    @Test
    @DisplayName("certificates answer every query like the in-memory ones, including expiry")
    void certificates() {
        var memory = scenario.system;
        assertThat(memory.certificates.findAll()).hasSize(3);
        sameAggregates(memory.certificates.findAll(), database.certificates().findAll());

        List<CertificateScope> scopes = List.of(CertificateScope.global(),
                CertificateScope.of(DomainWorld.ELECTRICAL), CertificateScope.of(DomainWorld.PRESSURE),
                CertificateScope.of(DomainWorld.BUILDING_SAFETY));
        for (Certificate certificate : memory.certificates.findAll()) {
            sameOptional(memory.certificates.findById(certificate.id()), database.certificates().findById(certificate.id()));
            sameAggregates(memory.certificates.findByBackingInspection(certificate.backingInspectionId()),
                    database.certificates().findByBackingInspection(certificate.backingInspectionId()));
        }
        for (var inspection : memory.inspections.findAll()) {
            for (CertificateScope scope : scopes) {
                sameOptional(memory.certificates.findByBackingInspection(inspection.id(), scope),
                        database.certificates().findByBackingInspection(inspection.id(), scope));
            }
        }
        for (Asset asset : memory.catalogue.assets.findAll()) {
            for (CertificateScope scope : scopes) {
                sameOptional(memory.certificates.findNonExpiredForAsset(asset.id(), scope),
                        database.certificates().findNonExpiredForAsset(asset.id(), scope));
                sameOptional(memory.certificates.findLatestForAsset(asset.id(), scope),
                        database.certificates().findLatestForAsset(asset.id(), scope));
            }
        }

        Certificate any = memory.certificates.findAll().getFirst();
        Instant expiry = any.validity().expiresAt();
        for (Instant moment : List.of(expiry.minusSeconds(1), expiry.minusNanos(1), expiry, expiry.plusNanos(1),
                expiry.plusSeconds(86_400), any.validity().issuedAt())) {
            sameAggregates(memory.certificates.findDueForExpiry(moment), database.certificates().findDueForExpiry(moment));
        }
        assertThat(database.certificates().findDueForExpiry(expiry.plusSeconds(1))).isNotEmpty();
        assertThat(database.certificates().findDueForExpiry(expiry.minusSeconds(1))).isEmpty();
    }

    @Test
    @DisplayName("the audit trail comes back complete and in the order it was written")
    void auditTrail() {
        var memory = scenario.system.auditTrail;
        assertThat(memory.all()).isNotEmpty();
        sameDocuments(memory.all(), database.auditTrail().all());
        for (AuditEntry entry : memory.all()) {
            AuditedElementRef element = entry.element();
            sameDocuments(memory.entriesFor(element), database.auditTrail().entriesFor(element));
            sameDocuments(memory.entriesFor(element, entry.action()),
                    database.auditTrail().entriesFor(element, entry.action()));
        }
        assertThat(database.auditTrail().entriesFor(AuditedElementRef.asset("nobody"))).isEmpty();
        assertThat(database.auditTrail().entriesFor(AuditedElementRef.asset("nobody"), AuditAction.INSPECTION_CLOSED))
                .isEmpty();
    }

    // ---- helpers

    private <T> void sameAggregates(List<T> expected, List<T> actual) {
        sameDocuments(expected, actual);
    }

    private <T> void sameOptional(Optional<T> expected, Optional<T> actual) {
        assertThat(actual.isPresent()).isEqualTo(expected.isPresent());
        expected.ifPresent(value -> assertThat(codec.write(actual.get())).isEqualTo(codec.write(value)));
    }

    private <T> void sameDocuments(List<T> expected, List<T> actual) {
        Function<T, String> document = codec::write;
        assertThat(actual.stream().map(document).toList()).isEqualTo(new ArrayList<>(expected.stream().map(document).toList()));
    }
}
