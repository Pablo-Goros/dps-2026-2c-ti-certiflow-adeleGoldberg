package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

/** Input contract implemented by the application use case. */
public interface RecordAnswer {
    Inspection record(InspectionId inspectionId, CriterionId criterionId, Answer answer);
}
