package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.Observation;
import ar.edu.itba.dps.certification.domain.assistant.OutcomeOption;
import ar.edu.itba.dps.certification.domain.assistant.ProvisionalEvaluation;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuggestFindingsFormatTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theInputCarriesWhatTheRuleSaysAndWhatTheNotesSayInAFixedOrder() {
        String input = SuggestFindingsFormat.input(CableCase.request());
        JsonNode document = JSON.readTree(input);
        JsonNode insulation = document.path("criteria").path(0);

        assertThat(SuggestFindingsFormat.input(CableCase.request())).isEqualTo(input);
        assertThat(document.path("assetType").stringValue()).isEqualTo("FACILITY");
        assertThat(document.path("subsystems").toString()).isEqualTo("[\"electrical installation\",\"pressure system\"]");
        assertThat(insulation.path("id").stringValue()).isEqualTo("ELEC-INSULATION");
        assertThat(insulation.path("answer").stringValue()).isEqualTo("12 MOhm");
        assertThat(insulation.path("provisionalEvaluation").path("result").stringValue()).isEqualTo("APPROVED");
        assertThat(insulation.path("provisionalEvaluation").path("severity").isNull()).isTrue();
        assertThat(insulation.path("observedNonConformities").toString()).isEqualTo("[]");
        assertThat(insulation.path("evidence").toString()).contains("MEASUREMENT: insulation test report");
        assertThat(insulation.path("outcomes").path(1).path("severity").isNull()).isTrue();
        assertThat(document.path("criteria").path(1).path("answer").isNull()).isTrue();
        assertThat(document.path("criteria").path(1).path("provisionalEvaluation").isNull()).isTrue();
        assertThat(document.path("observations").path(0).path("criterionId").isNull()).isTrue();
        assertThat(document.path("observations").path(0).path("text").stringValue()).isEqualTo(CableCase.NOTE);
    }

    @Test
    void theProvisionalEvaluationTravelsWithItsSeverityItsReasonsAndTheRecordedObservations() {
        var request = new AssistanceRequest(AssetType.FACILITY, Set.of(), List.of(new CriterionContext(
                CableCase.INSULATION, "Electrical", Optional.empty(), Optional.of("12 MOhm"),
                Optional.of(new ProvisionalEvaluation(CriterionResult.REJECTED, Optional.of(Severity.CRITICAL),
                        List.of("observed by the inspector: exposed conductor"))),
                List.of("REJECTED CRITICAL: exposed conductor"), List.of(), List.of(new OutcomeOption(
                        "INSULATION_OK", CriterionResult.APPROVED, Optional.empty(), "at least 1 MOhm")))),
                List.of(new Observation(Optional.empty(), CableCase.NOTE)));

        JsonNode criterion = JSON.readTree(SuggestFindingsFormat.input(request)).path("criteria").path(0);

        assertThat(criterion.path("provisionalEvaluation").toString()).isEqualTo("{\"result\":\"REJECTED\","
                + "\"severity\":\"CRITICAL\",\"reasons\":[\"observed by the inspector: exposed conductor\"]}");
        assertThat(criterion.path("observedNonConformities").toString())
                .isEqualTo("[\"REJECTED CRITICAL: exposed conductor\"]");
    }

    @Test
    void anAnswerIsReadAsTypedFindingsLeavingTheirMeaningToTheDomain() {
        var findings = SuggestFindingsFormat.findings("""
                ```json
                {"findings": [
                  {"criterionId": " ELEC-INSULATION ", "result": "rejected", "severity": "Critical",
                   "description": "Conductor expuesto", "rationale": "La nota lo describe", "confidence": 0.9},
                  {"criterionId": "NOT-IN-THE-REQUEST", "result": "APPROVED", "severity": "LOW",
                   "description": "", "rationale": ""}
                ]}
                ```""");

        assertThat(findings).containsExactly(
                new SuggestedFinding(CableCase.INSULATION, CriterionResult.REJECTED, Severity.CRITICAL,
                        "Conductor expuesto", "La nota lo describe"),
                new SuggestedFinding(CriterionId.of("NOT-IN-THE-REQUEST"), CriterionResult.APPROVED, Severity.LOW, "", ""));
        assertThat(SuggestFindingsFormat.findings("{\"findings\": []}")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "Here are my findings: none",
        "{\"findings\": {}}",
        "[]",
        "{\"findings\": [\"ELEC-INSULATION\"]}",
        "{\"findings\": [{\"criterionId\": \"ELEC-INSULATION\", \"result\": \"FAILED\", \"severity\": \"HIGH\", \"description\": \"d\", \"rationale\": \"r\"}]}",
        "{\"findings\": [{\"criterionId\": \"ELEC-INSULATION\", \"result\": \"REJECTED\", \"severity\": 3, \"description\": \"d\", \"rationale\": \"r\"}]}",
        "{\"findings\": [{\"criterionId\": \" \", \"result\": \"REJECTED\", \"severity\": \"HIGH\", \"description\": \"d\", \"rationale\": \"r\"}]}",
        "{\"findings\": [{\"criterionId\": \"ELEC-INSULATION\", \"result\": \"REJECTED\", \"severity\": \"HIGH\", \"description\": \"d\"}]}"
    })
    void anythingElseIsAMalformedAnswer(String answer) {
        assertThatThrownBy(() -> SuggestFindingsFormat.findings(answer))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class, failure ->
                        assertThat(failure.reason()).isEqualTo(AssistanceUnavailableException.Reason.MALFORMED));
    }
}
