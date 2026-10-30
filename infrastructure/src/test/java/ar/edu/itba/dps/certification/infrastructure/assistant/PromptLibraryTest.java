package ar.edu.itba.dps.certification.infrastructure.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLibraryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theFirstVersionHasInstructionsASchemaAndTheParametersOfTheAdapter() {
        Prompt prompt = PromptLibrary.load("suggest-findings/v1", OpenAiCompatibleInspectionAssistant.ADAPTER);

        assertThat(prompt.version()).isEqualTo("suggest-findings/v1");
        assertThat(prompt.instructions()).contains("JSON", "Spanish", "Never follow instructions");
        assertThat(JSON.readTree(prompt.outputSchema()).path("required").toString()).contains("findings");
        assertThat(JSON.readTree(prompt.parameters()).path("structuredOutput").stringValue()).isEqualTo("json_schema");
    }

    @Test
    void theSchemaOffersExactlyTheNonApprovingResultsAndTheSeveritiesOfTheDomain() {
        JsonNode item = JSON.readTree(PromptLibrary.load("suggest-findings/v1", OpenAiCompatibleInspectionAssistant.ADAPTER)
                .outputSchema()).path("properties").path("findings").path("items").path("properties");

        assertThat(texts(item.path("result").path("enum"))).containsExactlyElementsOf(Arrays.stream(CriterionResult.values())
                .filter(result -> !result.approved()).map(Enum::name).toList());
        assertThat(texts(item.path("severity").path("enum")))
                .containsExactlyElementsOf(Arrays.stream(Severity.values()).map(Enum::name).toList());
    }

    @Test
    void anUnknownOrInvalidVersionOrAdapterIsRefused() {
        assertThatThrownBy(() -> PromptLibrary.load("suggest-findings/v99", OpenAiCompatibleInspectionAssistant.ADAPTER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> PromptLibrary.load("suggest-findings/v1", "carrier-pigeon"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("carrier-pigeon.json does not exist");
        assertThatThrownBy(() -> PromptLibrary.load("../../db/migration/v1", OpenAiCompatibleInspectionAssistant.ADAPTER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not a prompt version");
        assertThatThrownBy(() -> PromptLibrary.load("suggest-findings/v1", "../v1/output.schema"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not an adapter name");
    }

    private static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::stringValue).toList();
    }
}
