package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumericAndYesNoRuleEdgeTest {

    @Test
    @DisplayName("numeric rules expose their declared domain and outcomes")
    void numericRulesExposeTheirDeclaredDomainAndOutcomes() {
        NumericRangeRule rule = validRule();

        assertThat(rule.unit()).isEqualTo("c");
        assertThat(rule.domainMinimum()).isEqualByComparingTo("-10");
        assertThat(rule.domainMaximum()).isEqualByComparingTo("10");
        assertThat(rule.bands()).hasSize(2);
        assertThat(rule.declaredOutcomes()).extracting(RuleOutcome::result)
                .containsExactlyInAnyOrder(CriterionResult.APPROVED, CriterionResult.REJECTED);
    }

    @Test
    @DisplayName("numeric admissibility rejects wrong shape, wrong unit and values outside the domain")
    void numericAdmissibilityRejectsInvalidAnswers() {
        NumericRangeRule rule = validRule();

        assertThat(rule.admissibilityViolation(YesNoAnswer.yes()))
                .contains("a numeric measurement is required");
        assertThat(rule.admissibilityViolation(Measurement.of("5", "f")))
                .hasValueSatisfying(violation -> assertThat(violation)
                        .contains("measurement must be expressed in c"));
        assertThat(rule.admissibilityViolation(Measurement.of("-11", "c")))
                .hasValueSatisfying(violation -> assertThat(violation)
                        .contains("outside the admissible range"));
        assertThat(rule.admissibilityViolation(Measurement.of("11", "c")))
                .hasValueSatisfying(violation -> assertThat(violation)
                        .contains("outside the admissible range"));
        assertThat(rule.admissibilityViolation(Measurement.of("10", "c"))).isEmpty();
    }

    @Test
    @DisplayName("numeric evaluation refuses inadmissible answers and reports uncovered admissible values")
    void numericEvaluationRejectsInadmissibleAndUncoveredValues() {
        NumericRangeRule gapRule = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", true, "4", true, RuleOutcome.approved("OK", "ok")),
                band("6", true, "10", true, RuleOutcome.rejected("BAD", Severity.HIGH, "bad"))));

        assertThatThrownBy(() -> validRule().evaluate(OptionAnswer.of("yes")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot evaluate an inadmissible answer");
        assertThatThrownBy(() -> gapRule.evaluate(Measurement.of("5", "c")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no band matches 5 c");
    }

    @Test
    @DisplayName("numeric publication detects empty domains and malformed bands before later checks")
    void numericPublicationDetectsEarlyViolations() {
        NumericRangeRule emptyDomain = new NumericRangeRule("c", bd("10"), bd("10"),
                List.of(band("10", true, "10", true, RuleOutcome.approved("OK", "ok"))));
        NumericRangeRule invertedBand = new NumericRangeRule("c", bd("0"), bd("10"),
                List.of(band("8", true, "2", true, RuleOutcome.rejected("BAD", Severity.HIGH, "bad"))));

        assertThat(emptyDomain.publicationViolations())
                .containsExactly("admissible domain [10, 10] is empty");
        assertThat(invertedBand.publicationViolations())
                .singleElement()
                .asString()
                .contains("has its bounds inverted");
    }

    @Test
    @DisplayName("numeric publication separately detects exclusive start and end boundaries")
    void numericPublicationDetectsExclusiveStartAndEndBoundaries() {
        NumericRangeRule exclusiveStart = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", false, "10", true, RuleOutcome.approved("OK", "ok"))));
        NumericRangeRule exclusiveEnd = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", true, "10", false, RuleOutcome.approved("OK", "ok"))));

        assertThat(exclusiveStart.publicationViolations())
                .singleElement()
                .asString()
                .contains("start at the admissible minimum");
        assertThat(exclusiveEnd.publicationViolations())
                .singleElement()
                .asString()
                .contains("end at the admissible maximum");
    }

    @Test
    @DisplayName("numeric bands honor every combination of inclusive and exclusive bounds")
    void numericBandsHonorInclusiveAndExclusiveBounds() {
        NumericBand openClosed = band("0", false, "10", true, RuleOutcome.approved("OK", "ok"));
        NumericBand closedOpen = band("0", true, "10", false, RuleOutcome.approved("OK", "ok"));

        assertThat(openClosed.contains(bd("0"))).isFalse();
        assertThat(openClosed.contains(bd("10"))).isTrue();
        assertThat(closedOpen.contains(bd("0"))).isTrue();
        assertThat(closedOpen.contains(bd("10"))).isFalse();
        assertThat(openClosed.describe()).isEqualTo("(0, 10]");
        assertThat(closedOpen.wellFormed()).isTrue();
    }

    @Test
    @DisplayName("yes/no rules cover both answers and reject other answer types")
    void yesNoRulesCoverBothAnswersAndRejectOthers() {
        YesNoRule rule = new YesNoRule(
                RuleOutcome.approved("YES", "yes"),
                RuleOutcome.observed("NO", Severity.LOW, "no"));

        assertThat(rule.admissibilityViolation(YesNoAnswer.no())).isEmpty();
        assertThat(rule.admissibilityViolation(Measurement.of("1", "c")))
                .contains("a yes/no answer is required");
        assertThat(rule.evaluate(YesNoAnswer.yes()).result()).isEqualTo(CriterionResult.APPROVED);
        assertThat(rule.evaluate(YesNoAnswer.no()).result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(rule.declaredOutcomes()).hasSize(2);
        assertThat(rule.publicationViolations()).isEmpty();
        assertThatThrownBy(() -> rule.evaluate(Measurement.of("1", "c")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("yes/no answer is required");
    }

    @Test
    @DisplayName("rule outcomes enforce the severity contract")
    void ruleOutcomesEnforceSeverityContract() {
        assertThatThrownBy(() -> new RuleOutcome("BAD", CriterionResult.APPROVED, Severity.LOW, "bad"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("approving outcome carries no severity");
        assertThatThrownBy(() -> new RuleOutcome("BAD", CriterionResult.REJECTED, null, "bad"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("non-approving one must carry it");
    }

    private NumericRangeRule validRule() {
        return new NumericRangeRule("c", bd("-10"), bd("10"), List.of(
                band("-10", true, "0", false, RuleOutcome.rejected("LOW", Severity.HIGH, "low")),
                band("0", true, "10", true, RuleOutcome.approved("OK", "ok"))));
    }

    private static NumericBand band(String lower, boolean lowerInclusive, String upper,
            boolean upperInclusive, RuleOutcome outcome) {
        return new NumericBand(bd(lower), lowerInclusive, bd(upper), upperInclusive, outcome);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
