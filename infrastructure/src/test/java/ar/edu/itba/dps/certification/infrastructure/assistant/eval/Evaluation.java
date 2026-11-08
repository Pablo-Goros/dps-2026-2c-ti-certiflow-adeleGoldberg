package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scorecard.Run;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the golden set through any implementation of the assistant port: the real adapter in the
 * harness, a stub in the CI. An assistant that cannot answer a case is a failed run in the
 * scorecard, not the end of the evaluation.
 */
public final class Evaluation {

    private Evaluation() {
    }

    /**
     * @param model the configured model, kept when the provider never reports the one that answered
     */
    public static Scorecard run(List<GoldenCase> cases, InspectionAssistant assistant, int repetitions,
            String prompt, String model) {
        if (repetitions < 1) {
            throw new IllegalArgumentException("repetitions must be at least 1");
        }
        List<Run> runs = new ArrayList<>();
        String answeredBy = null;
        for (int repetition = 1; repetition <= repetitions; repetition++) {
            for (GoldenCase goldenCase : cases) {
                long start = System.nanoTime();
                try {
                    AssistantReply reply = assistant.suggest(goldenCase.request());
                    answeredBy = answeredBy == null ? reply.version().model() : answeredBy;
                    runs.add(new Run(goldenCase.id(), goldenCase.expectsNothing(), repetition,
                            Optional.of(Scoring.score(goldenCase, reply.findings())), Optional.empty(),
                            Duration.ofNanos(System.nanoTime() - start)));
                } catch (AssistanceUnavailableException e) {
                    runs.add(new Run(goldenCase.id(), goldenCase.expectsNothing(), repetition, Optional.empty(),
                            Optional.of(e.reason() + ": " + e.getMessage()), Duration.ofNanos(System.nanoTime() - start)));
                }
            }
        }
        return new Scorecard(prompt, answeredBy == null ? model : answeredBy, repetitions, Instant.now(), runs);
    }
}
