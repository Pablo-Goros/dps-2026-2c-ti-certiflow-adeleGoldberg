package ar.edu.itba.dps.certification.domain.inspection.port.inbound;

import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;

/** Input contract implemented by the application use case. */
public interface CorrectNote {
    InspectionNote correct(InspectionId inspectionId, String noteId, String text);
}
