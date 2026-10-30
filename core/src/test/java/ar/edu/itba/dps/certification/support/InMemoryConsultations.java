package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryConsultations implements ConsultationRepository {

    private final Map<ConsultationId, Consultation> stored = new LinkedHashMap<>();

    @Override
    public void save(Consultation consultation) {
        stored.put(consultation.id(), consultation);
    }

    @Override
    public Optional<Consultation> findById(ConsultationId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public List<Consultation> findByInspection(InspectionId inspectionId) {
        return stored.values().stream()
                .filter(consultation -> consultation.inspectionId().equals(inspectionId))
                .toList();
    }
}
