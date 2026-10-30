package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.infrastructure.assistant.OpenAiCompatibleInspectionAssistant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The provider switch: a missing provider is a 503 per consultation, a broken setup stops the start. */
class AssistantConfigTest {

    private static final String LOCAL = "http://localhost:11434/v1";
    private static final String PROMPT = "suggest-findings/v1";

    private final AssistantConfig config = new AssistantConfig();

    @Test
    void withoutAProviderEveryConsultationIsUnavailableAsMisconfigured() {
        var assistant = config.inspectionAssistant("none", "", "", "", PROMPT, 60);

        assertThatThrownBy(() -> assistant.suggest(null))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class, failure -> assertThat(failure.reason())
                        .isEqualTo(AssistanceUnavailableException.Reason.MISCONFIGURED));
    }

    @Test
    void theOpenAiCompatibleProviderNeedsAnAddressAModelAndAnExistingPrompt() {
        assertThat(config.inspectionAssistant("openai-compatible", LOCAL, "llama3.1:8b", "", PROMPT, 60))
                .isInstanceOf(OpenAiCompatibleInspectionAssistant.class);
        assertThatThrownBy(() -> config.inspectionAssistant("openai-compatible", " ", "llama3.1:8b", "", PROMPT, 60))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("certiflow.assistant.base-url");
        assertThatThrownBy(() -> config.inspectionAssistant("openai-compatible", LOCAL, "", "", PROMPT, 60))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("certiflow.assistant.model");
        assertThatThrownBy(() -> config.inspectionAssistant("openai-compatible", LOCAL, "llama3.1:8b", "",
                "suggest-findings/v9", 60)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownProviderStopsTheStart() {
        assertThatThrownBy(() -> config.inspectionAssistant("carrier-pigeon", "", "", "", PROMPT, 60))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("carrier-pigeon");
    }
}
