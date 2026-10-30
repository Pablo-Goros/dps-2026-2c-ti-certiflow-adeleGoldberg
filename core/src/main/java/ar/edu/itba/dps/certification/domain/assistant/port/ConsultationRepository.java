package ar.edu.itba.dps.certification.domain.assistant.port;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

import java.util.List;
import java.util.Optional;

public interface ConsultationRepository {

    void save(Consultation consultation);

    Optional<Consultation> findById(ConsultationId id);

    List<Consultation> findByInspection(InspectionId inspectionId);

    default Consultation require(ConsultationId id) {
        return findById(id).orElseThrow(() -> new DomainException("consultation " + id + " does not exist"));
    }
}
