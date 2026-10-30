package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Calls the aggregate directly: its guards must hold whoever the caller is. */
class ObservedNonConformityTest {

    private static final CriterionId ELEC = DomainWorld.ELECTRICAL_WIRING;
    private static final CriterionId SAFE = DomainWorld.BUILDING_EXITS;

    private FullSystem system;
    private Party inspector;
    private InspectionId inspectionId;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        Asset asset = system.registerAsset.register("Central Facility", AssetType.FACILITY,
                responsible.id(), "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("REFERENCE"));
        inspectionId = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
    }

    @Test
    @DisplayName("a criterion whose rule approves the answer is rejected by what the inspector observed")
    void anApprovedAnswerIsRejectedByAnObservation() {
        observe("o-1", CriterionResult.REJECTED, Severity.CRITICAL);

        CriterionEvaluation evaluation = closeAnswering("clean");

        assertThat(inspection().requireRecord(ELEC).answer()).contains(OptionAnswer.of("clean"));
        assertThat(evaluation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(evaluation.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(evaluation.reasons()).singleElement()
                .isInstanceOf(EvaluationReason.ObservedByInspector.class)
                .satisfies(reason -> assertThat(reason.describe()).contains("exposed conductor"));
    }

    @Test
    @DisplayName("the rule and the observation combine: the worst result and the highest severity win")
    void theWorstOfRuleAndObservationWins() {
        observe("o-1", CriterionResult.OBSERVED, Severity.HIGH);

        CriterionEvaluation evaluation = closeAnswering("untidy");

        assertThat(evaluation.result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(evaluation.severity()).isEqualTo(Severity.HIGH);
        assertThat(evaluation.reasons()).hasSize(2);
    }

    @Test
    @DisplayName("an observation withdrawn before closing no longer weighs on the evaluation")
    void aWithdrawnObservationNoLongerWeighs() {
        observe("o-1", CriterionResult.REJECTED, Severity.CRITICAL);

        ObservedNonConformity withdrawn = inspection().withdrawObservedNonConformity(inspector.id(), ELEC, "o-1");

        assertThat(withdrawn.id()).isEqualTo("o-1");
        assertThat(closeAnswering("clean").result()).isEqualTo(CriterionResult.APPROVED);
    }

    @Test
    @DisplayName("an observation needs the assigned inspector, an applicable criterion, a non-approving result and a known note")
    void theAggregateGuardsEveryObservation() {
        Instant now = system.clock.now();

        assertThatThrownBy(() -> inspection().recordObservedNonConformity(PartyId.of("intruder"), ELEC, "o-1",
                CriterionResult.REJECTED, Severity.HIGH, "seen", Optional.empty(), now))
                .hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> inspection().recordObservedNonConformity(inspector.id(), SAFE, "o-1",
                CriterionResult.REJECTED, Severity.HIGH, "seen", Optional.empty(), now))
                .hasMessageContaining("does not have");
        assertThatThrownBy(() -> inspection().recordObservedNonConformity(inspector.id(), ELEC, "o-1",
                CriterionResult.APPROVED, Severity.HIGH, "seen", Optional.empty(), now))
                .hasMessageContaining("cannot approve");
        assertThatThrownBy(() -> inspection().recordObservedNonConformity(inspector.id(), ELEC, "o-1",
                CriterionResult.REJECTED, Severity.HIGH, "seen", Optional.of("missing-note"), now))
                .hasMessageContaining("note missing-note does not exist");
        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();

        observe("o-1", CriterionResult.REJECTED, Severity.HIGH);
        assertThatThrownBy(() -> observe("o-1", CriterionResult.OBSERVED, Severity.LOW))
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> inspection().withdrawObservedNonConformity(inspector.id(), ELEC, "o-9"))
                .hasMessageContaining("is not recorded");
        assertThat(inspection().requireRecord(ELEC).observations()).hasSize(1);
    }

    @Test
    @DisplayName("after closure an observation is withdrawn only by an auditable rectification")
    void afterClosureOnlyARectificationWithdrawsIt() {
        observe("o-1", CriterionResult.REJECTED, Severity.CRITICAL);
        closeAnswering("clean");

        assertThatThrownBy(() -> observe("o-2", CriterionResult.OBSERVED, Severity.LOW))
                .hasMessageContaining("while it is CLOSED");
        assertThatThrownBy(() -> inspection().withdrawObservedNonConformity(inspector.id(), ELEC, "o-1"))
                .hasMessageContaining("while it is CLOSED");

        Rectification rectification = inspection().rectify(RectificationId.of("r-1"), inspector.id(),
                system.clock.now(), "the cable belongs to the neighbouring building",
                List.of(new Correction.ObservationWithdrawal(ELEC, "o-1")));

        assertThat(rectification.changes()).singleElement().satisfies(change -> {
            assertThat(change.affectedCriterion()).contains(ELEC);
            assertThat(change.previousValue()).contains("exposed conductor");
            assertThat(change.currentValue()).isNull();
        });
        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();
        assertThat(inspection().requireRecord(ELEC).currentEvaluation().orElseThrow().result())
                .isEqualTo(CriterionResult.APPROVED);
    }

    @Test
    @DisplayName("a rectification that withdraws the same observation twice is refused without partial changes")
    void aDuplicateWithdrawalChangesNothing() {
        observe("o-1", CriterionResult.REJECTED, Severity.CRITICAL);
        closeAnswering("clean");
        var withdrawal = new Correction.ObservationWithdrawal(ELEC, "o-1");

        assertThatThrownBy(() -> inspection().rectify(RectificationId.of("r-1"), inspector.id(),
                system.clock.now(), "duplicated", List.of(withdrawal, withdrawal)))
                .hasMessageContaining("twice");

        assertThat(inspection().requireRecord(ELEC).observations()).hasSize(1);
        assertThat(inspection().rectifications()).isEmpty();
    }

    @Test
    @DisplayName("a note that supports an observation stays until the observation is withdrawn")
    void aNoteThatSupportsAnObservationStays() {
        String noteId = system.recordNote.record(inspectionId, Optional.empty(), "cable pelado junto al tablero").id();
        inspection().recordObservedNonConformity(inspector.id(), ELEC, "o-1", CriterionResult.REJECTED,
                Severity.CRITICAL, "exposed conductor next to the panel", Optional.of(noteId), system.clock.now());

        assertThatThrownBy(() -> inspection().removeNote(inspector.id(), noteId))
                .hasMessageContaining("supports observed non-conformity o-1");
        assertThat(inspection().notes()).hasSize(1);

        inspection().withdrawObservedNonConformity(inspector.id(), ELEC, "o-1");
        inspection().removeNote(inspector.id(), noteId);

        assertThat(inspection().notes()).isEmpty();
    }

    private Inspection inspection() {
        return system.inspections.require(inspectionId);
    }

    private ObservedNonConformity observe(String id, CriterionResult result, Severity severity) {
        return inspection().recordObservedNonConformity(inspector.id(), ELEC, id, result, severity,
                "exposed conductor next to the panel", Optional.empty(), system.clock.now());
    }

    private CriterionEvaluation closeAnswering(String electricalAnswer) {
        inspection().recordAnswer(inspector.id(), ELEC, OptionAnswer.of(electricalAnswer));
        inspection().close(inspector.id(), system.clock.now());
        return inspection().requireRecord(ELEC).currentEvaluation().orElseThrow();
    }
}
