package ar.edu.itba.dps.certification.domain.inspection.rectification;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record RectificationId(String value) {

    public RectificationId {
        value = Validate.requiredText(value, "rectification id");
    }

    public static RectificationId of(String value) {
        return new RectificationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
