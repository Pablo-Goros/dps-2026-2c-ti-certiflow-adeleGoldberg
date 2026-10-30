package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

/** Output the domain refused, kept with its reason: the evaluation harness counts it as invented. */
public record RejectedSuggestion(SuggestedFinding output, String reason) {

    public RejectedSuggestion {
        Validate.required(output, "rejected output");
        reason = Validate.requiredText(reason, "rejection reason");
    }
}
