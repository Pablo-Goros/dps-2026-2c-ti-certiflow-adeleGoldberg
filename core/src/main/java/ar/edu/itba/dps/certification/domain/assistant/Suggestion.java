package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public record Suggestion(SuggestionId id, CriterionId criterionId, CriterionResult result, Severity severity,
        String description, String rationale, Optional<SuggestionDecision> decision) {

    public Suggestion {
        Validate.required(id, "suggestion id");
        Validate.required(criterionId, "criterion id");
        Validate.required(result, "suggested result");
        Validate.ensure(!result.approved(), "an approving result is not a finding");
        Validate.required(severity, "suggested severity");
        description = Validate.requiredText(description, "suggested description");
        rationale = Validate.requiredText(rationale, "suggestion rationale");
        Validate.required(decision, "decision");
    }

    public boolean decided() {
        return decision.isPresent();
    }

    Suggestion decidedAs(SuggestionDecision made) {
        Validate.ensure(decision.isEmpty(), "suggestion " + id + " is already decided");
        return new Suggestion(id, criterionId, result, severity, description, rationale, Optional.of(made));
    }
}
