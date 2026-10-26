package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionClosureResult;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Input contract implemented by the application use case. */
public interface CloseInspection {
    InspectionClosureResult close(InspectionId inspectionId);
}
