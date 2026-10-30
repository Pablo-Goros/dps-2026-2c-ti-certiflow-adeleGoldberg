package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface RecordObservedNonConformity {
    ObservedNonConformity record(InspectionId inspectionId, CriterionId criterionId, CriterionResult result,
            Severity severity, String description, Optional<String> noteId);
}
