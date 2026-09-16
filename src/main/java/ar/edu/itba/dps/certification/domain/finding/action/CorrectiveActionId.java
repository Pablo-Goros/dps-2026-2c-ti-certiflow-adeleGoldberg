package ar.edu.itba.dps.certification.domain.finding.action;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record CorrectiveActionId(String value) {

    public CorrectiveActionId {
        value = Validate.requiredText(value, "corrective action id");
    }

    public static CorrectiveActionId of(String value) {
        return new CorrectiveActionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
