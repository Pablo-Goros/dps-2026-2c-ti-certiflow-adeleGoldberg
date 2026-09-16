package ar.edu.itba.dps.certification.domain.shared.answer;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.math.BigDecimal;

public record Measurement(BigDecimal value, String unit) implements Answer {

    public Measurement {
        Validate.required(value, "measurement value");
        unit = Validate.requiredText(unit, "measurement unit");
    }

    public static Measurement of(String value, String unit) {
        return new Measurement(new BigDecimal(value), unit);
    }

    @Override
    public String describe() {
        return value.toPlainString() + " " + unit;
    }
}
