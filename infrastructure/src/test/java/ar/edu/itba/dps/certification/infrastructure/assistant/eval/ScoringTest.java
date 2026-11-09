package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.infrastructure.assistant.eval.Scoring.CaseScore;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each kind of answer against the case that expects two findings (ELEC-PANEL REJECTED HIGH and
 * SAFE-EXITS OBSERVED MEDIUM): what counts as correct, wrong, invented or missed.
 */
class ScoringTest {

    private static final GoldenCase TWO_PROBLEMS = GoldenSet.load("06-dos-problemas");

    private static final SuggestedFinding PANEL = finding("ELEC-PANEL", CriterionResult.REJECTED, Severity.HIGH);
    private static final SuggestedFinding EXITS = finding("SAFE-EXITS", CriterionResult.OBSERVED, Severity.MEDIUM);

    @ParameterizedTest(name = "{0}")
    @MethodSource("answers")
    @DisplayName("classifies every suggestion and every expectation")
    void classifies(String answer, List<SuggestedFinding> findings, int correct, int rightSeverity, int wrongResult,
            int invented, int missed) {
        CaseScore score = Scoring.score(TWO_PROBLEMS, findings);

        assertThat(score.expected()).isEqualTo(2);
        assertThat(score.suggested()).isEqualTo(findings.size());
        assertThat(score.correct()).as("correct").isEqualTo(correct);
        assertThat(score.rightSeverity()).as("right severity").isEqualTo(rightSeverity);
        assertThat(score.wrongResult()).as("wrong result").isEqualTo(wrongResult);
        assertThat(score.invented()).as("invented").isEqualTo(invented);
        assertThat(score.missed()).as("missed").isEqualTo(missed);
        assertThat(score.mistakes()).hasSize(findings.size() + missed - rightSeverity);
    }

    static Stream<Arguments> answers() {
        return Stream.of(
                Arguments.of("exactly what was expected", List.of(PANEL, EXITS), 2, 2, 0, 0, 0),
                Arguments.of("the right finding with another severity",
                        List.of(PANEL, finding("SAFE-EXITS", CriterionResult.OBSERVED, Severity.LOW)), 2, 1, 0, 0, 0),
                Arguments.of("the right criterion with another result",
                        List.of(PANEL, finding("SAFE-EXITS", CriterionResult.REJECTED, Severity.CRITICAL)), 1, 1, 1, 0, 0),
                Arguments.of("a criterion nothing was expected for",
                        List.of(PANEL, EXITS, finding("PRES-VALVES", CriterionResult.REJECTED, Severity.HIGH)), 2, 2, 0, 1, 0),
                Arguments.of("a criterion outside the request, refused by the domain",
                        List.of(PANEL, EXITS, finding("NOT-THERE", CriterionResult.REJECTED, Severity.HIGH)), 2, 2, 0, 1, 0),
                Arguments.of("an approving result, refused by the domain",
                        List.of(PANEL, finding("SAFE-EXITS", CriterionResult.APPROVED, Severity.LOW)), 1, 1, 0, 1, 1),
                Arguments.of("a second finding for the same criterion",
                        List.of(PANEL, EXITS, finding("ELEC-PANEL", CriterionResult.OBSERVED, Severity.LOW)), 2, 2, 0, 1, 0),
                Arguments.of("only one of the two", List.of(PANEL), 1, 1, 0, 0, 1),
                Arguments.of("nothing at all", List.of(), 0, 0, 0, 0, 2));
    }

    private static SuggestedFinding finding(String criterionId, CriterionResult result, Severity severity) {
        return new SuggestedFinding(CriterionId.of(criterionId), result, severity, "descripción", "justificación");
    }
}
