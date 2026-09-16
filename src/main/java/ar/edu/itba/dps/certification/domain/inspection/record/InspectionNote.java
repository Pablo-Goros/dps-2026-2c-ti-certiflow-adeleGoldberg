package ar.edu.itba.dps.certification.domain.inspection.record;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

public record InspectionNote(
        String id,
        Optional<CriterionId> criterionId,
        String text,
        PartyId author,
        Instant recordedAt) {

    public InspectionNote {
        id = Validate.requiredText(id, "note id");
        Validate.required(criterionId, "note criterion criterionId");
        text = Validate.requiredText(text, "note text");
        Validate.required(author, "note author");
        Validate.required(recordedAt, "note instant");
    }

    public InspectionNote withText(String newText) {
        return new InspectionNote(id, criterionId, newText, author, recordedAt);
    }
}
