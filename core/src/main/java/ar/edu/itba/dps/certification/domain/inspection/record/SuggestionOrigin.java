package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.shared.Validate;

/**
 * Where an observed non-conformity came from when the assistant suggested it: the consultation and
 * suggestion, whether the inspector edited it before recording it, and which assistant answered.
 * The identifiers are plain text so the inspection does not depend on the assistant's model.
 */
public record SuggestionOrigin(String consultationId, String suggestionId, boolean edited, String assistant) {

    public SuggestionOrigin {
        consultationId = Validate.requiredText(consultationId, "originating consultation id");
        suggestionId = Validate.requiredText(suggestionId, "originating suggestion id");
        assistant = Validate.requiredText(assistant, "assistant that suggested it");
    }

    public String describe() {
        return "suggested by the assistant (" + assistant + ", consultation " + consultationId + "), "
                + (edited ? "edited" : "accepted") + " by the inspector";
    }
}
