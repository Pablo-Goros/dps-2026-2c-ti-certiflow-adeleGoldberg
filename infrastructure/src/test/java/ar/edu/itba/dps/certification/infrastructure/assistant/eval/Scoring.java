package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.RejectedSuggestion;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionScreening;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.GoldenCase.ExpectedFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares what the assistant suggested for a case with what the case expects. The answer first goes
 * through {@link SuggestionScreening}, the same rule the application applies, so what the domain would
 * refuse counts as invented here too.
 *
 * <ul>
 *   <li><b>correct</b>: the expected criterion with the expected result;</li>
 *   <li><b>right severity</b>: correct and with the expected severity;</li>
 *   <li><b>wrong result</b>: an expected criterion, but OBSERVED instead of REJECTED or the other way;</li>
 *   <li><b>invented</b>: a criterion nothing was expected for, or anything the domain refused;</li>
 *   <li><b>missed</b>: an expected finding nothing was suggested for.</li>
 * </ul>
 */
public final class Scoring {

    private Scoring() {
    }

    /** The comparison of one answer with one case, and a line describing each mistake. */
    public record CaseScore(String caseId, int expected, int suggested, int correct, int rightSeverity,
            int wrongResult, int invented, int missed, List<String> mistakes) {

        public CaseScore {
            mistakes = List.copyOf(mistakes);
        }
    }

    public static CaseScore score(GoldenCase goldenCase, List<SuggestedFinding> answer) {
        var screened = SuggestionScreening.screen(goldenCase.request(), answer);
        List<String> mistakes = new ArrayList<>();
        int correct = 0;
        int rightSeverity = 0;
        int wrongResult = 0;
        int invented = 0;
        for (SuggestedFinding finding : screened.accepted()) {
            Optional<ExpectedFinding> expected = goldenCase.expected().stream()
                    .filter(candidate -> candidate.criterionId().equals(finding.criterionId()))
                    .findFirst();
            if (expected.isEmpty()) {
                invented++;
                mistakes.add("inventado: " + describe(finding));
            } else if (expected.get().result() != finding.result()) {
                wrongResult++;
                mistakes.add("resultado equivocado: se esperaba " + expected.get() + ", sugirió " + describe(finding));
            } else {
                correct++;
                if (expected.get().severity() == finding.severity()) {
                    rightSeverity++;
                } else {
                    mistakes.add("severidad equivocada: se esperaba " + expected.get() + ", sugirió " + describe(finding));
                }
            }
        }
        for (RejectedSuggestion rejected : screened.rejected()) {
            invented++;
            mistakes.add("inventado (lo rechazó el dominio: " + rejected.reason() + "): " + describe(rejected.output()));
        }
        int missed = 0;
        for (ExpectedFinding expected : goldenCase.expected()) {
            boolean answered = screened.accepted().stream()
                    .anyMatch(finding -> finding.criterionId().equals(expected.criterionId()));
            if (!answered) {
                missed++;
                mistakes.add("omitido: " + expected);
            }
        }
        return new CaseScore(goldenCase.id(), goldenCase.expected().size(), answer.size(), correct, rightSeverity,
                wrongResult, invented, missed, mistakes);
    }

    private static String describe(SuggestedFinding finding) {
        return finding.criterionId() + " " + finding.result() + " " + finding.severity() + " «" + finding.description() + "»";
    }
}
