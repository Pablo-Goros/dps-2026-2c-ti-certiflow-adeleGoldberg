package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harness as the CI runs it: the same evaluation and report as against a real model, with a
 * deterministic stub of the port instead. A stub whose answers are known proves the metrics count
 * right; the model itself is measured apart, with {@code -Passistant-eval}.
 */
class HarnessWithStubIT {

    private static final AssistantVersion STUB = new AssistantVersion("stub", "scripted", "suggest-findings/v1");

    private final List<GoldenCase> cases = GoldenSet.all();
    private final Map<AssistanceRequest, GoldenCase> byRequest = new IdentityHashMap<>();

    HarnessWithStubIT() {
        cases.forEach(goldenCase -> byRequest.put(goldenCase.request(), goldenCase));
    }

    @Test
    @DisplayName("a stub that answers exactly what each case expects scores perfectly, three times over")
    void aPerfectAssistantScoresPerfectly() {
        Scorecard scorecard = Evaluation.run(cases, stub(this::expectedOf), 3, "suggest-findings/v1", "scripted");
        var totals = scorecard.totals();

        assertThat(scorecard.runs()).hasSize(cases.size() * 3);
        assertThat(totals.recall()).isEqualTo(1.0);
        assertThat(totals.precision()).isEqualTo(1.0);
        assertThat(totals.severityAccuracy()).isEqualTo(1.0);
        assertThat(totals.invented()).isZero();
        assertThat(totals.missed()).isZero();
        assertThat(totals.cleanNegatives()).isEqualTo(1.0);
        assertThat(totals.failedRuns()).isZero();
    }

    @Test
    @DisplayName("known mistakes are counted exactly: an invention on every negative case, a wrong severity, a miss")
    void knownMistakesAreCountedExactly() {
        int negatives = (int) cases.stream().filter(GoldenCase::expectsNothing).count();
        int expected = cases.stream().mapToInt(goldenCase -> goldenCase.expected().size()).sum();

        Scorecard scorecard = Evaluation.run(cases, stub(goldenCase -> switch (goldenCase.id()) {
            case "01-cable-pelado" -> List.of(new SuggestedFinding(goldenCase.expected().getFirst().criterionId(),
                    CriterionResult.REJECTED, Severity.LOW, "conductor expuesto", "la nota lo describe"));
            case "06-dos-problemas" -> expectedOf(goldenCase).subList(0, 1);
            default -> goldenCase.expectsNothing() ? List.of(inventedFor(goldenCase)) : expectedOf(goldenCase);
        }), 1, "suggest-findings/v1", "scripted");
        var totals = scorecard.totals();

        assertThat(totals.invented()).isEqualTo(negatives);
        assertThat(totals.cleanNegativeRuns()).isZero();
        assertThat(totals.correct()).isEqualTo(expected - 1);
        assertThat(totals.rightSeverity()).isEqualTo(expected - 2);
        assertThat(totals.missed()).isEqualTo(1);
        assertThat(totals.wrongResult()).isZero();
    }

    @Test
    @DisplayName("an assistant that cannot answer a case is a failed run, and the evaluation goes on")
    void anUnavailableAnswerIsAFailedRun() {
        Scorecard scorecard = Evaluation.run(cases, request -> {
            if (byRequest.get(request).id().equals("07-sin-novedades")) {
                throw new AssistanceUnavailableException(AssistanceUnavailableException.Reason.UNREACHABLE, "timeout");
            }
            return new AssistantReply(STUB, expectedOf(byRequest.get(request)));
        }, 2, "suggest-findings/v1", "scripted");

        assertThat(scorecard.totals().failedRuns()).isEqualTo(2);
        assertThat(scorecard.totals().runs()).isEqualTo(cases.size() * 2);
        assertThat(scorecard.totals().recall()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the report names the prompt and the model, every metric, every case and every mistake")
    void writesTheReport(@TempDir Path folder) throws Exception {
        Scorecard scorecard = Evaluation.run(cases, stub(goldenCase ->
                goldenCase.expectsNothing() ? List.of(inventedFor(goldenCase)) : expectedOf(goldenCase)),
                1, "suggest-findings/v1", "scripted");

        Path markdown = EvaluationReport.write(folder, scorecard);
        Path comparison = EvaluationReport.writeComparison(folder, List.of(scorecard, scorecard));

        assertThat(markdown.getFileName().toString()).isEqualTo("suggest-findings-v1__scripted.md");
        assertThat(folder.resolve("suggest-findings-v1__scripted.json")).exists();
        String report = Files.readString(markdown);
        assertThat(report).contains("`suggest-findings/v1` con `scripted`", "Hallazgos correctos", "Severidades acertadas",
                "Hallazgos inventados", "07-sin-novedades", "inventado:");
        cases.forEach(goldenCase -> assertThat(report).contains(goldenCase.id()));
        assertThat(Files.readString(comparison)).contains("| `suggest-findings/v1` | `scripted` | 100 %");
        assertThat(Files.readString(folder.resolve("suggest-findings-v1__scripted.json")))
                .contains("\"prompt\" : \"suggest-findings/v1\"", "\"invented\"");
    }

    private InspectionAssistant stub(Function<GoldenCase, List<SuggestedFinding>> answers) {
        return request -> new AssistantReply(STUB, answers.apply(byRequest.get(request)));
    }

    private List<SuggestedFinding> expectedOf(GoldenCase goldenCase) {
        return goldenCase.expected().stream()
                .map(expected -> new SuggestedFinding(expected.criterionId(), expected.result(), expected.severity(),
                        "lo que describe la nota", "la nota lo describe"))
                .toList();
    }

    private static SuggestedFinding inventedFor(GoldenCase goldenCase) {
        return new SuggestedFinding(goldenCase.request().criteria().getFirst().id(), CriterionResult.REJECTED,
                Severity.CRITICAL, "un problema que nadie vio", "copiado de la escala del criterio");
    }
}
