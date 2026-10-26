package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

/** Input contract implemented by the application use case. */
public interface RemoveEvidence {
    Inspection remove(InspectionId inspectionId, CriterionId criterionId, String evidenceId);
}
