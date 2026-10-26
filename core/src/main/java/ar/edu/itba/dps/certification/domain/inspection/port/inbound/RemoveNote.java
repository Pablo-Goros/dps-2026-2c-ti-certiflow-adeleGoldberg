package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Input contract implemented by the application use case. */
public interface RemoveNote {
    Inspection remove(InspectionId inspectionId, String noteId);
}
