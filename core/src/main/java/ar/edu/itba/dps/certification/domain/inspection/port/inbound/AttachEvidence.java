package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

/** Input contract implemented by the application use case. */
public interface AttachEvidence {
    EvidenceRecord attach(InspectionId inspectionId, CriterionId criterionId, String requirementLabel,
            String reference);
}
