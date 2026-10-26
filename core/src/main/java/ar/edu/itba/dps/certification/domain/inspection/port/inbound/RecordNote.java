package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface RecordNote {
    InspectionNote record(InspectionId inspectionId, Optional<CriterionId> criterionId, String text);
}
