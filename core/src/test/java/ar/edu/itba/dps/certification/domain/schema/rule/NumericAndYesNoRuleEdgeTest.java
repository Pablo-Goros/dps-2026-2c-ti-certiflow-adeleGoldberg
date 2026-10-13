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
    @DisplayName("numeric rules expose their declared range and outcomes")
    void numericRulesExposeTheirDeclaredRangeAndOutcomes() {
        NumericRangeRule rule = validRule();

        assertThat(rule.unit()).isEqualTo("c");
        assertThat(rule.rangeMinimum()).isEqualByComparingTo("-10");
        assertThat(rule.rangeMaximum()).isEqualByComparingTo("10");
        assertThat(rule.bands()).hasSize(2);
        assertThat(rule.withinRange(bd("10"))).isTrue();
        assertThat(rule.withinRange(bd("11"))).isFalse();
        assertThat(rule.declaredOutcomes()).hasSize(3).contains(rule.outOfRange());
        assertThat(rule.declaredOutcomes()).extracting(RuleOutcome::result)
                .contains(CriterionResult.APPROVED, CriterionResult.REJECTED);
    }

    @Test
    @DisplayName("numeric admissibility rejects wrong shape and wrong unit, but admits any value")
    void numericAdmissibilityRejectsInvalidAnswers() {
        NumericRangeRule rule = validRule();

        assertThat(rule.admissibilityViolation(YesNoAnswer.yes()))
                .contains("a numeric measurement is required");
        assertThat(rule.admissibilityViolation(Measurement.of("5", "f")))
                .hasValueSatisfying(violation -> assertThat(violation)
                        .contains("measurement must be expressed in c"));
        assertThat(rule.admissibilityViolation(Measurement.of("10", "c"))).isEmpty();
        // A reading off the declared range is a fact to record, not a malformed answer.
        assertThat(rule.admissibilityViolation(Measurement.of("-11", "c"))).isEmpty();
        assertThat(rule.admissibilityViolation(Measurement.of("11", "c"))).isEmpty();
    }

    @Test
    @DisplayName("a measurement outside the declared range is evaluated with the declared out-of-range outcome")
    void aMeasurementOutsideTheRangeGetsTheOutOfRangeOutcome() {
        NumericRangeRule rule = validRule();

        assertThat(rule.evaluate(Measurement.of("11", "c"))).isEqualTo(rule.outOfRange());
        assertThat(rule.evaluate(Measurement.of("-10.5", "c"))).isEqualTo(rule.outOfRange());
        assertThat(rule.evaluate(Measurement.of("11", "c")).result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(rule.evaluate(Measurement.of("10", "c")).code()).isEqualTo("OK");
    }

    @Test
    @DisplayName("numeric evaluation refuses answers of another shape, and a gap cannot survive publication")
    void numericEvaluationRejectsInadmissibleAnswers() {
        NumericRangeRule gapRule = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", true, "4", true, RuleOutcome.approved("OK", "ok")),
                band("6", true, "10", true, RuleOutcome.rejected("BAD", Severity.HIGH, "bad"))),
                offScale());

        assertThatThrownBy(() -> validRule().evaluate(OptionAnswer.of("yes")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot evaluate an inadmissible answer");
        // Inside the range an unmatched value falls back to the out-of-range outcome, which is why
        // publication refuses gaps: no published version can hold one.
        assertThat(gapRule.evaluate(Measurement.of("5", "c"))).isEqualTo(gapRule.outOfRange());
        assertThat(gapRule.publicationViolations()).anyMatch(violation -> violation.contains("leave a gap"));
    }

    @Test
    @DisplayName("numeric publication detects an empty range and malformed bands before later checks")
    void numericPublicationDetectsEarlyViolations() {
        NumericRangeRule emptyRange = new NumericRangeRule("c", bd("10"), bd("10"),
                List.of(band("10", true, "10", true, RuleOutcome.approved("OK", "ok"))), offScale());
        NumericRangeRule invertedBand = new NumericRangeRule("c", bd("0"), bd("10"),
                List.of(band("8", true, "2", true, RuleOutcome.rejected("BAD", Severity.HIGH, "bad"))),
                offScale());

        assertThat(emptyRange.publicationViolations())
                .containsExactly("declared range [10, 10] is empty");
        assertThat(invertedBand.publicationViolations())
                .singleElement()
                .asString()
                .contains("has its bounds inverted");
    }

    @Test
    @DisplayName("numeric publication separately detects exclusive start and end boundaries")
    void numericPublicationDetectsExclusiveStartAndEndBoundaries() {
        NumericRangeRule exclusiveStart = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", false, "10", true, RuleOutcome.approved("OK", "ok"))), offScale());
        NumericRangeRule exclusiveEnd = new NumericRangeRule("c", bd("0"), bd("10"), List.of(
                band("0", true, "10", false, RuleOutcome.approved("OK", "ok"))), offScale());

        assertThat(exclusiveStart.publicationViolations())
                .singleElement()
                .asString()
                .contains("start at the declared minimum");
        assertThat(exclusiveEnd.publicationViolations())
                .singleElement()
                .asString()
                .contains("end at the declared maximum");
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
                band("0", true, "10", true, RuleOutcome.approved("OK", "ok"))),
                offScale());
    }

    private static RuleOutcome offScale() {
        return RuleOutcome.rejected("OFF_SCALE", Severity.CRITICAL, "outside what the sensor reads");
    }

    private static NumericBand band(String lower, boolean lowerInclusive, String upper,
            boolean upperInclusive, RuleOutcome outcome) {
        return new NumericBand(bd(lower), lowerInclusive, bd(upper), upperInclusive, outcome);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
