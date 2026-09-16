package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record FindingId(String value) {

    public FindingId {
        value = Validate.requiredText(value, "finding id");
    }

    public static FindingId of(String value) {
        return new FindingId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
