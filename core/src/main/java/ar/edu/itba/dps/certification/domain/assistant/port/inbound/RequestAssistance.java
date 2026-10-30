package ar.edu.itba.dps.certification.domain.assistant.port.inbound;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

/** Input contract implemented by the application use case. */
public interface RequestAssistance {
    Consultation request(InspectionId inspectionId);
}
