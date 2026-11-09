package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.infrastructure.assistant.OpenAiCompatibleInspectionAssistant;
import ar.edu.itba.dps.certification.infrastructure.assistant.PromptLibrary;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The golden set against a real model, never part of the CI because it is slow, may cost money and
 * does not answer the same twice. It reports metrics instead of asserting them: a worse prompt is a
 * finding for the team, not a broken build.
 *
 * <pre>
 * mvn -pl infrastructure -am verify -Passistant-eval \
 *     -Dassistant.eval.model=llama3.1:8b \
 *     -Dassistant.eval.prompts=suggest-findings/v1,suggest-findings/v2
 * </pre>
 *
 * Settings (system properties): {@code assistant.eval.base-url} (Ollama on this machine by default),
 * {@code assistant.eval.model}, {@code assistant.eval.prompts}, {@code assistant.eval.repetitions},
 * {@code assistant.eval.timeout-seconds} and {@code assistant.eval.output}. The key, when the provider
 * needs one, comes from the {@code CERTIFLOW_ASSISTANT_API_KEY} environment variable.
 */
class SuggestFindingsHarness {

    @Test
    void measuresEachPromptVersionOverTheGoldenSet() {
        URI baseUrl = URI.create(setting("assistant.eval.base-url", "http://localhost:11434/v1"));
        String model = setting("assistant.eval.model", "llama3.2:3b");
        List<String> prompts = Arrays.stream(setting("assistant.eval.prompts", "suggest-findings/v1").split(","))
                .map(String::strip).filter(prompt -> !prompt.isEmpty()).toList();
        int repetitions = Integer.parseInt(setting("assistant.eval.repetitions", "3"));
        Duration timeout = Duration.ofSeconds(Long.parseLong(setting("assistant.eval.timeout-seconds", "300")));
        Path output = Path.of(setting("assistant.eval.output", "target/assistant-eval"));
        Optional<String> apiKey = Optional.ofNullable(System.getenv("CERTIFLOW_ASSISTANT_API_KEY"));
        List<GoldenCase> cases = GoldenSet.all();

        List<Scorecard> scorecards = new ArrayList<>();
        for (String prompt : prompts) {
            var assistant = new OpenAiCompatibleInspectionAssistant(baseUrl, model, apiKey,
                    PromptLibrary.load(prompt, OpenAiCompatibleInspectionAssistant.ADAPTER), timeout);
            Scorecard scorecard = Evaluation.run(cases, assistant, repetitions, prompt, model);
            scorecards.add(scorecard);
            Path report = EvaluationReport.write(output, scorecard);
            System.out.println("[assistant-eval] " + prompt + " con " + scorecard.model() + " -> " + report.toAbsolutePath());
        }
        Path comparison = EvaluationReport.writeComparison(output.resolve(model.replaceAll("[^A-Za-z0-9._-]+", "-")),
                scorecards);
        System.out.println(EvaluationReport.comparison(scorecards));
        System.out.println("[assistant-eval] comparación -> " + comparison.toAbsolutePath());

        assertThat(scorecards).allSatisfy(scorecard -> assertThat(scorecard.runs()).hasSize(cases.size() * repetitions));
    }

    private static String setting(String name, String fallback) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
