package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.adapter.certification.RegisteredCertificationPolicies;
import ar.edu.itba.dps.certification.adapter.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.ResponsiblePartyRef;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.support.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class UseCaseAtomicityIT {
    @ParameterizedTest
    @ValueSource(strings = {"issue", "renew"})
    void aPolicyResolutionFailureIsAuditedAfterTheBusinessTransactionRollsBack(String operation) {
        var database = TestDatabase.create();
        var clock = TestClock.at("2026-03-05T13:00:00Z");
        var audit = new AuditRecorder(database.auditTrail(), new FixedActor("operator"), clock);
        var assetId = AssetId.of("asset");
        var version = new SchemaVersionId(SchemaId.of("schema"), 1);
        var inspection = InspectionId.of("inspection");
        database.assets().save(new Asset(assetId, "Asset", AssetType.LABORATORY, Map.of(),
                new ResponsiblePartyRef(PartyId.of("owner"), "Owner"), "Here", TestPolicies.REFERENCE));
        database.inspections().save(CertificationScenarios.approvedInspection(inspection, assetId,
                version, Set.of(), clock.now()));
        if (operation.equals("renew")) {
            var old = Instant.parse("2025-03-04T13:00:00Z");
            var previousInspection = CertificationScenarios.approvedInspection(InspectionId.of("previous"),
                    assetId, version, Set.of(), old);
            database.certificates().save(CertificationScenarios.issue(CertificateId.of("previous"), previousInspection,
                    CertificateScope.global(), TestPolicies.profile("REFERENCE", 1, Set.of(), true, 12, 12).snapshot(), old, null));
        }
        var factory = new CertificateFactory(database.inspections(), new RepositoryFindingQuery(database.findings()),
                database.certificates(), new CatalogueAssetDirectory(database.assets(), clock),
                new RegisteredCertificationPolicies(), new SequentialIds(), clock);

        assertThatThrownBy(() -> {
            if (operation.equals("issue")) {
                new IssueCertificate(
                        factory, database.certificates(), audit, database.transactions()).issue(inspection);
            } else {
                new RenewCertificate(
                        factory, database.certificates(), audit, database.transactions()).renew(inspection);
            }
        }).isInstanceOf(PolicyResolutionException.class).hasMessageContaining("no certification policy registered");

        assertThat(database.certificates().findByBackingInspection(inspection)).isEmpty();
        assertThat(database.auditTrail().all()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.CERTIFICATE_ISSUANCE_BLOCKED);
            assertThat(entry.detail().toString()).contains(operation, "no certification policy registered");
        });
    }

    @Test void aDirectUseCaseCallRollsBackTheRepositoryIfItsAuditFailsWithoutAnyCallerTransaction() {
        var database = TestDatabase.create();
        AuditTrail trail = new AuditTrail() {
            @Override public void append(AuditEntry entry) { throw new IllegalStateException("audit unavailable"); }
            @Override public List<AuditEntry> entriesFor(AuditedElementRef element) {
                return database.auditTrail().entriesFor(element);
            }
            @Override public List<AuditEntry> entriesFor(AuditedElementRef element, AuditAction action) {
                return database.auditTrail().entriesFor(element, action);
            }
            @Override public List<AuditEntry> all() { return database.auditTrail().all(); }
        };
        var clock = TestClock.at("2026-03-05T13:00:00Z");
        var actor = new FixedActor("editor");
        ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RegisterParty failing = new RegisterParty(
                database.parties(), new SequentialIds(), new AuditRecorder(trail, actor, clock), database.transactions());
        assertThatThrownBy(() -> failing.register("Inspector", PartyKind.PERSON))
                .hasMessageContaining("audit unavailable");
        assertThat(database.parties().findAll()).isEmpty();
        assertThat(database.auditTrail().all()).isEmpty();

        ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RegisterParty working = new RegisterParty(
                database.parties(), new SequentialIds(), new AuditRecorder(database.auditTrail(), actor, clock), database.transactions());
        var party = working.register("Inspector", PartyKind.PERSON);
        assertThat(database.parties().findById(party.id())).isPresent();
        assertThat(database.auditTrail().all()).hasSize(1);
    }
}
