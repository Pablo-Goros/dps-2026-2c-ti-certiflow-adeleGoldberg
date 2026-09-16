package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditTrailIT {

    private FullSystem system;
    private Party inspector;
    private Party responsible;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishLaboratorySchema(AssetType.LABORATORY);
        inspector = system.person("Ana Perez");
        responsible = system.organization("Favaloro Foundation");
        asset = system.asset("Laboratory A", AssetType.LABORATORY, responsible);
    }

    @Test
    @DisplayName("a relocation keeps the previous and the new value")
    void aRelocationKeepsBothValues() {
        system.relocateAsset.relocate(asset.id(), "Building 7");

        assertThat(system.auditTrail.entriesFor(AuditedElementRef.asset(asset.id().value()),
                AuditAction.ASSET_RELOCATED)).singleElement().satisfies(entry -> {
                    assertThat(entry.detail()).isInstanceOfSatisfying(AuditDetail.DataChanged.class,
                            changed -> assertThat(changed.changes()).singleElement()
                                    .satisfies(change -> {
                                        assertThat(change.previousValue()).isEqualTo("Building 1");
                                        assertThat(change.currentValue()).isEqualTo("Building 7");
                                    }));
                    assertThat(entry.actor().displayName()).isEqualTo("operator");
                });
    }

    @Test
    @DisplayName("a change of responsible keeps both values too")
    void aResponsibleChangeKeepsBothValues() {
        Party other = system.organization("Merk Laboratories");

        system.changeAssetResponsible.change(asset.id(), other.id());

        assertThat(system.auditTrail.withAction(AuditAction.ASSET_RESPONSIBLE_CHANGED))
                .singleElement().satisfies(entry ->
                        assertThat(entry.detail()).isInstanceOfSatisfying(AuditDetail.DataChanged.class,
                                changed -> assertThat(changed.changes().getFirst().previousValue())
                                        .contains("Favaloro Foundation")));
    }

    @Test
    @DisplayName("closing an inspection is recorded as a decision")
    void closingIsRecordedAsADecision() {
        InspectionId inspectionId = inspectAndClose("30");

        assertThat(system.auditTrail.entriesFor(AuditedElementRef.inspection(inspectionId.value()),
                AuditAction.INSPECTION_CLOSED)).singleElement().satisfies(entry ->
                        assertThat(entry.detail()).isInstanceOf(AuditDetail.DecisionRecorded.class));
    }

    @Test
    @DisplayName("a rectification is recorded with its mandatory reason")
    void aRectificationCarriesItsReason() {
        InspectionId inspectionId = inspectAndClose("30");

        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(), "the probe was misread",
                List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE,
                        Measurement.of("5", "c"))));

        assertThat(system.auditTrail.withAction(AuditAction.INSPECTION_RECTIFIED))
                .singleElement().satisfies(entry ->
                        assertThat(entry.reason()).contains("the probe was misread"));
    }

    @Test
    @DisplayName("the overdue sweep is recorded as an automatic operation")
    void theSweepIsRecordedAsAutomatic() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrate", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        system.clock.advanceDays(45);
        system.expireActions.sweep();

        assertThat(system.auditTrail.withAction(AuditAction.CORRECTIVE_ACTION_EXPIRED))
                .singleElement().satisfies(entry ->
                        assertThat(entry.actor()).isInstanceOf(Actor.System.class));
    }

    @Test
    @DisplayName("the trail covers every element type RF10 names")
    void theTrailCoversEveryElementType() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrate", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        system.reportExecution.report(finding.id(), "done", List.of("file://a.jpg"),
                PartyId.of("executor"));
        system.verifyCorrectiveAction.verify(finding.id(), true, "within range", inspector.id());
        system.issueCertificate.issue(inspectionId);

        assertThat(system.auditTrail.all()).extracting(entry -> entry.element().type())
                .contains(AuditedElementRef.ElementType.ASSET,
                        AuditedElementRef.ElementType.SCHEMA,
                        AuditedElementRef.ElementType.INSPECTION,
                        AuditedElementRef.ElementType.FINDING,
                        AuditedElementRef.ElementType.CORRECTIVE_ACTION,
                        AuditedElementRef.ElementType.CERTIFICATE);
    }

    @Test
    @DisplayName("an action that requires a reason cannot be recorded without one")
    void anActionRequiringAReasonRefusesToBeRecordedWithoutIt() {
        assertThatThrownBy(() -> new AuditEntry(
                AuditedElementRef.inspection("inspection-1"),
                AuditAction.INSPECTION_RECTIFIED,
                Instant.parse("2026-03-01T10:00:00Z"),
                Actor.system(),
                Optional.empty(),
                AuditDetail.created("whatever")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("requires a reason");
    }

    private InspectionId inspectAndClose(String temperature) {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);
        return inspectionId;
    }
}
