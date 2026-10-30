package ar.edu.itba.dps.certification.domain.assistant.port.inbound;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseConsultations {
    /** The consultations about an inspection, newest first. */
    List<Consultation> of(InspectionId inspectionId);

    Optional<Consultation> find(ConsultationId consultationId);
}
