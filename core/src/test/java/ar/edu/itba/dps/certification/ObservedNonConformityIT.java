package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.inspection.usecase.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DirectTransactions;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

/** The case that motivated the concept: a correct reading next to a defect the reading cannot see. */
class ObservedNonConformityIT {

    private static final CriterionId ELEC = DomainWorld.ELECTRICAL_WIRING;

    private FullSystem system;
    private Party inspector;
    private Asset asset;
    private RecordObservedNonConformity recordObservation;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        asset = system.registerAsset.register("Central Facility", AssetType.FACILITY,
                responsible.id(), "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("REFERENCE"));
        recordObservation = new RecordObservedNonConformity(system.inspections, system.ids, system.clock,
                system.audit, system.actors, DirectTransactions.INSTANCE);
    }

    @Test
    @DisplayName("an exposed conductor blocks the electrical certificate without falsifying the reading")
    void anObservedDefectBlocksOnlyThePartItConcerns() {
        InspectionId id = inspectWithAnExposedConductor();

        var record = system.inspections.require(id).requireRecord(ELEC);
        assertThat(record.answer()).contains(OptionAnswer.of("clean"));
        assertThat(record.currentEvaluation().orElseThrow().result()).isEqualTo(CriterionResult.REJECTED);

        Finding finding = electricalFinding(id);
        assertThat(finding.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(finding.reasons()).anySatisfy(reason ->
                assertThat(reason.describe()).contains("exposed conductor"));

        assertThat(blockers(system.issueCertificate.issuePartial(id, DomainWorld.ELECTRICAL))).isNotEmpty();
        assertThat(issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.PRESSURE)).scope())
                .isEqualTo(CertificateScope.of(DomainWorld.PRESSURE));
    }

    @Test
    @DisplayName("withdrawing the observation by rectification voids its finding and the part can be certified")
    void aRectifiedObservationVoidsItsFinding() {
        InspectionId id = inspectWithAnExposedConductor();
        String observationId = system.inspections.require(id).requireRecord(ELEC).observations().getFirst().id();

        system.rectifyClosedInspection.rectify(id, "the cable belongs to the neighbouring building",
                List.of(new Correction.ObservationWithdrawal(ELEC, observationId)));

        assertThat(electricalFinding(id).obligationVoided()).isTrue();
        assertThat(issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.ELECTRICAL)).scope())
                .isEqualTo(CertificateScope.of(DomainWorld.ELECTRICAL));
    }

    @Test
    @DisplayName("recording the observation is audited as data the inspector recorded")
    void theObservationIsAudited() {
        InspectionId id = inspectWithAnExposedConductor();
        String observationId = system.inspections.require(id).requireRecord(ELEC).observations().getFirst().id();

        var entries = system.auditTrail.entriesFor(AuditedElementRef.inspection(id.value()),
                AuditAction.INSPECTION_DATA_RECORDED);

        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.detail().toString()).contains("observation." + ELEC + "." + observationId);
            assertThat(entry.actor().displayName()).isEqualTo(inspector.name());
        });
    }

    private InspectionId inspectWithAnExposedConductor() {
        InspectionId id = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, ELEC, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        var note = system.recordNote.record(id, Optional.empty(), "cable con la aislación pelada junto al tablero");
        recordObservation.record(id, ELEC, CriterionResult.REJECTED, Severity.CRITICAL,
                "exposed conductor next to the panel", Optional.of(note.id()));
        system.closeInspection.close(id);
        return id;
    }

    private Finding electricalFinding(InspectionId id) {
        return system.findings.findByInspection(id).stream()
                .filter(finding -> finding.criterionId().equals(ELEC))
                .findFirst().orElseThrow();
    }
}
