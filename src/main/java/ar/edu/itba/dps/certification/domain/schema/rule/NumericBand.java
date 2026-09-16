package ar.edu.itba.dps.certification.domain.schema.rule;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.math.BigDecimal;

public record NumericBand(
        BigDecimal lower,
        boolean lowerInclusive,
        BigDecimal upper,
        boolean upperInclusive,
        RuleOutcome outcome) {

    public NumericBand {
        Validate.required(lower, "band lower bound");
        Validate.required(upper, "band upper bound");
        Validate.required(outcome, "band outcome");
    }

    public boolean contains(BigDecimal value) {
        int fromLower = value.compareTo(lower);
        int toUpper = value.compareTo(upper);
        boolean aboveLower = lowerInclusive ? fromLower >= 0 : fromLower > 0;
        boolean belowUpper = upperInclusive ? toUpper <= 0 : toUpper < 0;
        return aboveLower && belowUpper;
    }

    public boolean wellFormed() {
        return lower.compareTo(upper) <= 0;
    }

    public String describe() {
        return (lowerInclusive ? "[" : "(") + lower.toPlainString() + ", " + upper.toPlainString()
                + (upperInclusive ? "]" : ")");
    }
}
