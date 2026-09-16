package ar.edu.itba.dps.certification.domain.inspection;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record InspectionId(String value) {

    public InspectionId {
        value = Validate.requiredText(value, "inspection id");
    }

    public static InspectionId of(String value) {
        return new InspectionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
