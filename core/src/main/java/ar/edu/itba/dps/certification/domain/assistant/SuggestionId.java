package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record SuggestionId(String value) {

    public SuggestionId {
        value = Validate.requiredText(value, "suggestion id");
    }

    public static SuggestionId of(String value) {
        return new SuggestionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
