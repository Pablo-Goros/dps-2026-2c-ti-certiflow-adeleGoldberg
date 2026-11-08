package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import ar.edu.itba.dps.certification.domain.assistant.CriterionContext;
import ar.edu.itba.dps.certification.domain.assistant.OutcomeOption;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The golden set is data: this checks it is well formed, so a careless edit fails the build, not the harness. */
class GoldenSetTest {

    private final List<GoldenCase> cases = GoldenSet.all();

    @Test
    @DisplayName("has at least ten cases, with unique ids, both with and without expected findings")
    void hasEnoughCasesOfBothKinds() {
        assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
        assertThat(cases).extracting(GoldenCase::id).doesNotHaveDuplicates();
        assertThat(cases).filteredOn(GoldenCase::expectsNothing).hasSizeGreaterThanOrEqualTo(3);
        assertThat(cases).filteredOn(goldenCase -> !goldenCase.expectsNothing()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("every expected finding names a criterion of its request and an outcome that criterion can produce")
    void everyExpectationIsAnswerable() {
        assertThat(cases).allSatisfy(goldenCase -> assertThat(goldenCase.expected()).allSatisfy(expected -> {
            CriterionContext criterion = goldenCase.request().criterion(expected.criterionId()).orElseThrow(
                    () -> new AssertionError(goldenCase.id() + " expects " + expected.criterionId()
                            + ", which is not in its request"));
            assertThat(expected.result().approved()).as(goldenCase.id() + ": an approval is not a finding").isFalse();
            assertThat(criterion.outcomes()).as(goldenCase.id() + ": the scale of " + expected.criterionId())
                    .extracting(OutcomeOption::result, OutcomeOption::severity)
                    .contains(tuple(expected.result(), Optional.of(expected.severity())));
        }));
    }

    @Test
    @DisplayName("expects at most one finding per criterion, as the domain keeps at most one")
    void atMostOneFindingPerCriterion() {
        assertThat(cases).allSatisfy(goldenCase -> assertThat(goldenCase.expected())
                .extracting(GoldenCase.ExpectedFinding::criterionId).doesNotHaveDuplicates());
    }
}
