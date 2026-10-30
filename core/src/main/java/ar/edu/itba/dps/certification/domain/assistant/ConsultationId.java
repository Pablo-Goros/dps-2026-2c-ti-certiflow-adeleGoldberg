package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record ConsultationId(String value) {

    public ConsultationId {
        value = Validate.requiredText(value, "consultation id");
    }

    public static ConsultationId of(String value) {
        return new ConsultationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
