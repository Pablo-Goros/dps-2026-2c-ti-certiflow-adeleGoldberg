package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.Validate;

/**
 * A finding as the assistant proposed it. Only typed; whether it makes sense for the inspection is
 * decided by {@link Consultation}, which keeps the invalid ones with their reason.
 */
public record SuggestedFinding(CriterionId criterionId, CriterionResult result, Severity severity, String description,
        String rationale) {

    public SuggestedFinding {
        Validate.required(criterionId, "suggested criterion");
        Validate.required(result, "suggested result");
        Validate.required(severity, "suggested severity");
        Validate.required(description, "suggested description");
        Validate.required(rationale, "suggestion rationale");
    }
}
