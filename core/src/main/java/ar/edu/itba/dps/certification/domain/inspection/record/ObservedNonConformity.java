package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

/**
 * A non-conformity the inspector saw that the criterion's rule does not measure, such as an exposed
 * conductor next to an insulation reading that is within range. It weighs on the criterion's
 * evaluation like the rule's own verdict.
 */
public record ObservedNonConformity(
        String id,
        CriterionResult result,
        Severity severity,
        String description,
        Optional<String> noteId,
        PartyId recordedBy,
        Instant recordedAt) {

    public ObservedNonConformity {
        id = Validate.requiredText(id, "observed non-conformity id");
        Validate.required(result, "observed result");
        Validate.ensure(!result.approved(), "an observed non-conformity cannot approve a criterion");
        Validate.required(severity, "observed severity");
        description = Validate.requiredText(description, "observed non-conformity description");
        Validate.required(noteId, "observed non-conformity note id");
        Validate.required(recordedBy, "observed non-conformity author");
        Validate.required(recordedAt, "observed non-conformity instant");
    }

    public String describe() {
        return result + " " + severity + ": " + description;
    }
}
