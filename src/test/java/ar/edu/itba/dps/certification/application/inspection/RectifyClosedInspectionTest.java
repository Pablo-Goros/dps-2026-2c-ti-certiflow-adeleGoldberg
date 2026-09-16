package ar.edu.itba.dps.certification.application.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionRecord;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;
import java.util.List;

class RectifyClosedInspectionTest {

    private DomainWorld world;
    private AssetType laboratory;
    private Party inspector;
    private Asset asset;
    private InspectionId inspectionId;

    @BeforeEach
    void setUp() {
        world = new DomainWorld();
        laboratory = AssetType.LABORATORY;
        world.publishLaboratorySchema(laboratory);
        inspector = world.person("Ana Perez");
        Party responsible = world.organization("Favaloro Foundation");
        asset = world.asset("Laboratory A", laboratory, responsible, "Building 1");
        inspectionId = world.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        world.startInspection.start(inspectionId);
        world.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        world.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL,
                "file://manual.pdf");
    }

    @Test
    @DisplayName("a rectification that removes the non-conformity voids the obligation and keeps the original result")
    void removingTheNonConformityVoidsTheObligation() {
        closeWith("30");

        rectifyTemperatureTo("5");

        CriterionRecord record = world.inspections.require(inspectionId)
                .requireRecord(DomainWorld.TEMPERATURE);
        assertThat(record.evaluations()).hasSize(2);
        assertThat(record.originalEvaluation().orElseThrow().result())
                .isEqualTo(CriterionResult.REJECTED);
        assertThat(record.currentEvaluation().orElseThrow().result())
                .isEqualTo(CriterionResult.APPROVED);
        assertThat(world.findings.voided).singleElement()
                .satisfies(call -> assertThat(call.criterionId()).isEqualTo(DomainWorld.TEMPERATURE));
        assertThat(world.findings.revised).isEmpty();
        assertThat(world.events.ofType(CriterionResultRevised.class)).singleElement()
                .satisfies(event -> assertThat(event.nonConformityRemoved()).isTrue());
    }

    @Test
    @DisplayName("a rectification revealing a non-conformity on an approved criterion creates a new finding")
    void revealingANonConformityRegistersANewFinding() {
        closeWith("5");

        rectifyTemperatureTo("30");

        assertThat(world.findings.revealed).singleElement()
                .satisfies(call -> assertThat(call.criterionId()).isEqualTo(DomainWorld.TEMPERATURE));
        assertThat(world.events.ofType(CriterionResultRevised.class)).singleElement()
                .satisfies(event -> assertThat(event.becameRejected()).isTrue());
    }

    @Test
    @DisplayName("a non-conformity that persists with a different result revises the existing finding")
    void aPersistingNonConformityRevisesTheExistingFinding() {
        closeWith("30");

        rectifyTemperatureTo("20");

        assertThat(world.findings.revised).singleElement()
                .satisfies(call -> assertThat(call.evaluation().result())
                        .isEqualTo(CriterionResult.OBSERVED));
        assertThat(world.findings.voided).isEmpty();
        assertThat(world.findings.revealed).isEmpty();
    }

    @Test
    @DisplayName("re-evaluation uses the version frozen at start, not a newer published one")
    void reevaluationUsesTheOriginalVersion() {
        closeWith("30");
        publishASecondVersionWhereWarmIsCritical();

        rectifyTemperatureTo("20");

        assertThat(world.inspections.require(inspectionId).requireRecord(DomainWorld.TEMPERATURE)
                .currentEvaluation().orElseThrow().severity()).isEqualTo(Severity.LOW);
    }

    @Test
    @DisplayName("only the assigned inspector may rectify")
    void onlyTheAssignedInspectorMayRectify() {
        closeWith("30");
        PartyId someoneElse = world.person("Other Inspector").id();

        assertThatThrownBy(() -> world.rectifyClosedInspection.rectify(inspectionId, someoneElse,
                "typo in the reading", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of("5", "c")))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only the assigned inspector");
    }

    @Test
    @DisplayName("an open inspection cannot be rectified")
    void anOpenInspectionCannotBeRectified() {
        world.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("30", "c"));

        assertThatThrownBy(() -> rectifyTemperatureTo("5"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only a closed inspection");
    }

    @Test
    @DisplayName("a correction that does not change the outcome appends no new evaluation")
    void aCorrectionWithoutEffectAppendsNothing() {
        closeWith("30");

        rectifyTemperatureTo("40");

        CriterionRecord record = world.inspections.require(inspectionId)
                .requireRecord(DomainWorld.TEMPERATURE);
        assertThat(record.evaluations()).hasSize(1);
        assertThat(world.findings.revised).isEmpty();
        assertThat(world.events.published).isEmpty();
    }

    @Test
    @DisplayName("the rectification is recorded on the inspection with its mandatory reason")
    void theRectificationIsKeptWithItsReason() {
        closeWith("30");

        rectifyTemperatureTo("5");

        Inspection inspection = world.inspections.require(inspectionId);
        assertThat(inspection.rectifications()).singleElement().satisfies(rectification -> {
            assertThat(rectification.reason()).isEqualTo("typo in the reading");
            assertThat(rectification.author()).isEqualTo(inspector.id());
            assertThat(rectification.affectedCriteria()).containsExactly(DomainWorld.TEMPERATURE);
        });
    }

    private void closeWith(String temperature) {
        world.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c"));
        world.closeInspection.close(inspectionId);
    }

    private void rectifyTemperatureTo(String temperature) {
        world.rectifyClosedInspection.rectify(inspectionId, inspector.id(), "typo in the reading",
                List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE,
                        Measurement.of(temperature, "c"))));
    }

    private void publishASecondVersionWhereWarmIsCritical() {
        InspectionSchema schema = world.schemas.findByApplicableAssetType(laboratory).orElseThrow();
        world.openDraft.open(schema.id());
        world.editDraft.removeSection(schema.id(), "Safety");
        world.editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(Severity.CRITICAL),
                DomainWorld.documentationCriterion()));
        assertThat(world.publishSchemaVersion.publish(schema.id()).published()).isTrue();
    }
}
