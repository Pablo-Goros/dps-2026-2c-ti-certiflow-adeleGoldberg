package ar.edu.itba.dps.certification.application.browse;

import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.application.certification.usecase.BrowseCertificates;
import ar.edu.itba.dps.certification.application.finding.usecase.BrowseFindings;
import ar.edu.itba.dps.certification.application.inspection.usecase.BrowseInspections;
import ar.edu.itba.dps.certification.application.schema.usecase.BrowseSchemas;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

/** The read side the REST controllers use, over the in-memory adapters. */
class BrowseUseCasesTest {

    private FullSystem system;
    private Party owner;
    private Party inspector;
    private InspectionSchema schema;
    private Instant firstVersionInForce;

    private BrowseParties parties;
    private BrowseSchemas schemas;
    private BrowseInspections inspections;
    private BrowseFindings findings;
    private BrowseCertificates certificates;

    @BeforeEach
    void aLaboratorySchemaAndTheReadSide() {
        system = new FullSystem();
        owner = system.organization("Owner");
        inspector = system.person("Inspector");
        system.actAs(inspector);
        schema = system.createSchema.create("Laboratory", Set.of(AssetType.LABORATORY));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(Severity.MEDIUM), DomainWorld.documentationCriterion()));
        system.publishSchemaVersion.publish(schema.id());
        firstVersionInForce = system.clock.now();

        parties = new BrowseParties(system.catalogue.parties);
        schemas = new BrowseSchemas(system.schemas);
        inspections = new BrowseInspections(system.inspections, system.schemaCatalog);
        findings = new BrowseFindings(system.findings);
        certificates = new BrowseCertificates(system.certificates);
    }

    private Asset laboratory() {
        return system.registerAsset.register("Lab", AssetType.LABORATORY, owner.id(), "Here", Map.of(),
                JurisdictionId.of("A"));
    }

    private InspectionId closedInspection(Asset asset, String reading) {
        var id = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.TEMPERATURE, Measurement.of(reading, "c"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "manual");
        system.closeInspection.close(id);
        return id;
    }

    @Test
    void partiesAreListedAndFoundById() {
        assertThat(parties.all()).extracting(Party::id).contains(owner.id(), inspector.id());
        assertThat(parties.find(owner.id())).map(Party::name).contains("Owner");
        assertThat(parties.find(PartyId.of("nobody"))).isEmpty();
    }

    @Test
    void inspectionsAreFilteredByAssetInspectorAndStatusTogether() {
        var first = laboratory();
        var second = laboratory();
        var closed = closedInspection(first, "5");
        var assigned = system.assignInspection.assign(second.id(), inspector.id(), system.clock.today()).id();

        assertThat(inspections.find(closed)).isPresent();
        assertThat(inspections.find(InspectionId.of("missing"))).isEmpty();
        assertThat(inspections.search(Optional.empty(), Optional.empty(), Optional.empty())).hasSize(2);
        assertThat(inspections.search(Optional.of(first.id()), Optional.empty(), Optional.empty()))
                .extracting(i -> i.id()).containsExactly(closed);
        assertThat(inspections.search(Optional.empty(), Optional.of(inspector.id()), Optional.of(InspectionStatus.ASSIGNED)))
                .extracting(i -> i.id()).containsExactly(assigned);
        assertThat(inspections.search(Optional.of(first.id()), Optional.empty(), Optional.of(InspectionStatus.ASSIGNED)))
                .isEmpty();
        assertThat(inspections.search(Optional.empty(), Optional.of(owner.id()), Optional.empty())).isEmpty();
    }

    @Test
    void anInspectionExposesTheSchemaVersionItFrozeWhenItStarted() {
        var asset = laboratory();
        var assigned = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id();
        assertThat(inspections.frozenVersion(inspections.find(assigned).orElseThrow())).isEmpty();

        system.startInspection.start(assigned);

        var frozen = inspections.frozenVersion(inspections.find(assigned).orElseThrow());
        assertThat(frozen).isPresent();
        assertThat(frozen.get().number()).isEqualTo(1);
    }

    @Test
    void findingsAreFilteredByInspectionAssetAndOpenActions() {
        var observed = laboratory();
        var fine = laboratory();
        var withFinding = closedInspection(observed, "20");
        closedInspection(fine, "5");

        assertThat(findings.search(Optional.empty(), Optional.empty(), false)).hasSize(1);
        assertThat(findings.search(Optional.of(withFinding), Optional.empty(), false)).hasSize(1);
        assertThat(findings.search(Optional.empty(), Optional.of(observed.id()), true)).hasSize(1);
        assertThat(findings.search(Optional.empty(), Optional.of(fine.id()), false)).isEmpty();
        var id = findings.search(Optional.empty(), Optional.empty(), true).getFirst().id();
        assertThat(findings.find(id)).isPresent();
    }

    @Test
    void certificatesAreFilteredByAssetInspectionAndStatus() {
        var asset = laboratory();
        var inspection = closedInspection(asset, "5");
        Certificate issued = issuedCertificate(system.issueCertificate.issue(inspection));

        assertThat(certificates.find(issued.id())).isPresent();
        assertThat(certificates.search(Optional.empty(), Optional.empty(), Optional.empty())).containsExactly(issued);
        assertThat(certificates.search(Optional.of(asset.id()), Optional.of(inspection), Optional.of(CertificateStatus.VALID)))
                .containsExactly(issued);
        assertThat(certificates.search(Optional.empty(), Optional.empty(), Optional.of(CertificateStatus.EXPIRED))).isEmpty();
        assertThat(certificates.search(Optional.of(laboratory().id()), Optional.empty(), Optional.empty())).isEmpty();
    }

    @Test
    void theVersionInForceOnADateFollowsTheSchedule() {
        system.clock.advanceDays(10);
        system.openDraft.open(schema.id()); // starts as a copy of version 1
        Instant secondTakesOver = system.clock.now().plus(Duration.ofDays(30));
        system.publishSchemaVersion.publish(schema.id(), secondTakesOver);

        assertThat(schemas.versionInForceAt(schema.id(), firstVersionInForce.minusSeconds(1))).isEmpty();
        assertThat(numberAt(firstVersionInForce)).isEqualTo(1);
        assertThat(numberAt(secondTakesOver.minusSeconds(1))).isEqualTo(1);
        assertThat(numberAt(secondTakesOver)).isEqualTo(2);
        assertThat(numberAt(secondTakesOver.plus(Duration.ofDays(400)))).isEqualTo(2);
    }

    @Test
    void versionsAreLookedUpByNumberAndMissingOnesAreEmpty() {
        assertThat(schemas.all()).hasSize(1);
        assertThat(schemas.find(schema.id())).isPresent();
        assertThat(schemas.version(schema.id(), 1)).isPresent();
        assertThat(schemas.version(schema.id(), 7)).isEmpty();
        assertThat(schemas.version(ar.edu.itba.dps.certification.domain.schema.SchemaId.of("nope"), 1)).isEmpty();
        assertThat(schemas.versionInForceAt(ar.edu.itba.dps.certification.domain.schema.SchemaId.of("nope"),
                firstVersionInForce)).isEmpty();
    }

    private int numberAt(Instant moment) {
        return schemas.versionInForceAt(schema.id(), moment).orElseThrow().number();
    }
}
