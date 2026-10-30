package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

/** The text of a note, tied to a criterion or to the inspection as a whole. */
public record Observation(Optional<CriterionId> criterionId, String text) {

    public Observation {
        Validate.required(criterionId, "observation criterion");
        text = Validate.requiredText(text, "observation text");
    }
}
