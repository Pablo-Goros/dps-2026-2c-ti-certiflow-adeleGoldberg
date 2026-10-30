package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistanceRequestsTest {

    private FullSystem system;
    private Party inspector;
    private Asset asset;
    private SchemaVersion frozen;
    private InspectionId inspectionId;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        frozen = system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        asset = system.registerAsset.register("Central Facility", AssetType.FACILITY, responsible.id(), "Building 1",
                Map.of("room", "12", "purpose", "research"), Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE),
                JurisdictionId.of("REFERENCE"));
        inspectionId = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
    }

    @Test
    @DisplayName("the request carries the applicable criteria in schema order, with what the rule engine says today")
    void theRequestDescribesTheApplicableCriteria() {
        note(Optional.empty(), "cable con la aislación pelada junto al tablero");
        note(Optional.of(DomainWorld.PRESSURE_VALVES), "la válvula pierde");

        AssistanceRequest request = AssistanceRequests.of(inspection(), frozen, system.clock.now());

        assertThat(request.assetType()).isEqualTo(AssetType.FACILITY);
        assertThat(request.subsystems()).containsExactly(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE);
        assertThat(request.criteria()).extracting(CriterionContext::id).containsExactly(
                DomainWorld.ELECTRICAL_WIRING, DomainWorld.PRESSURE_VALVES, DomainWorld.DOCUMENTATION);
        CriterionContext electrical = request.criterion(DomainWorld.ELECTRICAL_WIRING).orElseThrow();
        assertThat(electrical.section()).isEqualTo("Electrical");
        assertThat(electrical.subsystem()).contains(DomainWorld.ELECTRICAL);
        assertThat(electrical.answer()).contains("clean");
        assertThat(electrical.provisional())
                .contains(new ProvisionalEvaluation(CriterionResult.APPROVED, Optional.empty(), List.of()));
        assertThat(electrical.observedNonConformities()).isEmpty();
        assertThat(electrical.outcomes()).extracting(OutcomeOption::code).containsExactly("HK_HAZARD", "HK_OK", "HK_UNTIDY");
        CriterionContext pressure = request.criterion(DomainWorld.PRESSURE_VALVES).orElseThrow();
        assertThat(pressure.answer()).isEmpty();
        assertThat(pressure.provisional()).isEmpty();
        assertThat(request.criterion(DomainWorld.DOCUMENTATION).orElseThrow().evidence())
                .containsExactly("DOCUMENT: " + DomainWorld.SAFETY_MANUAL);
        assertThat(request.observations()).extracting(Observation::criterionId)
                .containsExactly(Optional.empty(), Optional.of(DomainWorld.PRESSURE_VALVES));
    }

    @Test
    @DisplayName("the request names no inspection, asset or person, and is the same every time")
    void theRequestCarriesNoIdentitiesAndIsDeterministic() {
        note(Optional.empty(), "cable con la aislación pelada junto al tablero");

        AssistanceRequest request = AssistanceRequests.of(inspection(), frozen, system.clock.now());

        assertThat(request.toString()).doesNotContain(inspectionId.value(), asset.id().value(), asset.name(),
                inspector.id().value(), inspector.name());
        assertThat(AssistanceRequests.of(inspection(), frozen, system.clock.now())).isEqualTo(request);
    }

    @Test
    @DisplayName("without notes there is nothing for the assistant to read")
    void anInspectionWithoutNotesIsRefused() {
        assertThatThrownBy(() -> AssistanceRequests.of(inspection(), frozen, system.clock.now()))
                .hasMessageContaining("has no notes");
    }

    @Test
    @DisplayName("the request is built only from the version the inspection froze")
    void anotherVersionIsRefused() {
        note(Optional.empty(), "seen on site");
        SchemaVersion other = system.publishLaboratorySchema(AssetType.LABORATORY);

        assertThatThrownBy(() -> AssistanceRequests.of(inspection(), other, system.clock.now()))
                .hasMessageContaining("is not the one");
    }

    @Test
    @DisplayName("each criterion shows what closing now would conclude, why, and what the inspector already recorded")
    void theRequestShowsTheProvisionalEvaluationAndTheRecordedObservations() {
        note(Optional.empty(), "cable con la aislación pelada junto al tablero");
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("untidy"));
        inspection().recordObservedNonConformity(inspector.id(), DomainWorld.ELECTRICAL_WIRING, "o-1",
                CriterionResult.REJECTED, Severity.CRITICAL, "exposed conductor next to the panel", Optional.empty(),
                system.clock.now());

        AssistanceRequest request = AssistanceRequests.of(inspection(), frozen, system.clock.now());

        CriterionContext electrical = request.criterion(DomainWorld.ELECTRICAL_WIRING).orElseThrow();
        assertThat(electrical.observedNonConformities())
                .containsExactly("REJECTED CRITICAL: exposed conductor next to the panel");
        assertThat(electrical.provisional()).contains(new ProvisionalEvaluation(CriterionResult.REJECTED,
                Optional.of(Severity.CRITICAL), List.of("observed by the inspector: exposed conductor next to the panel")));
        assertThat(request.criterion(DomainWorld.PRESSURE_VALVES).orElseThrow().provisional()).contains(
                new ProvisionalEvaluation(CriterionResult.OBSERVED, Optional.of(Severity.MEDIUM), List.of("area is untidy")));
    }

    @Test
    @DisplayName("outcomes that share a code still come in a fixed order")
    void outcomesThatShareACodeKeepAFixedOrder() {
        var present = new OutcomeOption("DOC", CriterionResult.APPROVED, Optional.empty(), "manual present");
        var missing = new OutcomeOption("DOC", CriterionResult.REJECTED, Optional.of(Severity.HIGH), "manual missing");

        assertThat(Stream.of(missing, present).sorted(AssistanceRequests.OUTCOME_ORDER)).containsExactly(present, missing);
        assertThat(Stream.of(present, missing).sorted(AssistanceRequests.OUTCOME_ORDER)).containsExactly(present, missing);
    }

    private void note(Optional<CriterionId> criterionId, String text) {
        system.recordNote.record(inspectionId, criterionId, text);
    }

    private Inspection inspection() {
        return system.inspections.require(inspectionId);
    }
}
