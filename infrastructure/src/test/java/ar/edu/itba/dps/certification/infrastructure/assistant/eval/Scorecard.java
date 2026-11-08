package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scoring.CaseScore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Every run of one prompt version with one model over the golden set, and the metrics summed over
 * them. The same case runs several times because the model does not always answer the same.
 */
public record Scorecard(String prompt, String model, int repetitions, Instant ranAt, List<Run> runs) {

    public Scorecard {
        runs = List.copyOf(runs);
    }

    /** One answer to one case: its score, or why the assistant could not answer. */
    public record Run(String caseId, boolean expectsNothing, int repetition, Optional<CaseScore> score,
            Optional<String> failure, Duration took) {
    }

    /** Sums over a set of runs; ratios are NaN when their denominator is zero. */
    public record Totals(int runs, int failedRuns, int expected, int suggested, int correct, int rightSeverity,
            int wrongResult, int invented, int missed, int negativeRuns, int cleanNegativeRuns) {

        /** Of everything suggested, how much was right. */
        public double precision() {
            return ratio(correct, suggested);
        }

        /** Of everything expected, how much was found. */
        public double recall() {
            return ratio(correct, expected);
        }

        /** Of the correct findings, how many had the expected severity. */
        public double severityAccuracy() {
            return ratio(rightSeverity, correct);
        }

        public double inventedPerRun() {
            return ratio(invented, runs - failedRuns);
        }

        /** Of the runs of cases that expect nothing, how many suggested nothing. */
        public double cleanNegatives() {
            return ratio(cleanNegativeRuns, negativeRuns);
        }

        private static double ratio(int part, int whole) {
            return whole == 0 ? Double.NaN : (double) part / whole;
        }
    }

    public Totals totals() {
        return totals(run -> true);
    }

    /** The totals of one repetition over the whole set, to show how much one pass differs from another. */
    public Totals totals(int repetition) {
        return totals(run -> run.repetition() == repetition);
    }

    private Totals totals(Predicate<Run> which) {
        List<Run> selected = runs.stream().filter(which).toList();
        List<CaseScore> scores = selected.stream().flatMap(run -> run.score().stream()).toList();
        List<Run> negatives = selected.stream().filter(run -> run.expectsNothing() && run.score().isPresent()).toList();
        return new Totals(
                selected.size(),
                (int) selected.stream().filter(run -> run.failure().isPresent()).count(),
                scores.stream().mapToInt(CaseScore::expected).sum(),
                scores.stream().mapToInt(CaseScore::suggested).sum(),
                scores.stream().mapToInt(CaseScore::correct).sum(),
                scores.stream().mapToInt(CaseScore::rightSeverity).sum(),
                scores.stream().mapToInt(CaseScore::wrongResult).sum(),
                scores.stream().mapToInt(CaseScore::invented).sum(),
                scores.stream().mapToInt(CaseScore::missed).sum(),
                negatives.size(),
                (int) negatives.stream().filter(run -> run.score().orElseThrow().suggested() == 0).count());
    }
}
