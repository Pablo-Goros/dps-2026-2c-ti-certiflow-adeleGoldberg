package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record CriterionId(String value) {

    public CriterionId {
        value = Validate.requiredText(value, "criterion id");
    }

    public static CriterionId of(String value) {
        return new CriterionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
