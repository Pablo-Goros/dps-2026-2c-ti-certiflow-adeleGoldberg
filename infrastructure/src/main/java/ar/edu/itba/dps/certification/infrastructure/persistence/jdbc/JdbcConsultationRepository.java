package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The assistant's consultations, one document each. Deciding a suggestion rewrites the document
 * under the row version it was read with, so two concurrent decisions cannot both be kept.
 */
public final class JdbcConsultationRepository extends DocumentRepository<Consultation>
        implements ConsultationRepository {

    public JdbcConsultationRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Consultation.class, "consultation");
    }

    @Override
    public void save(Consultation consultation) {
        Map<String, Object> indexed = columns();
        indexed.put("inspection_id", consultation.inspectionId().value());
        store(consultation.id().value(), consultation, indexed);
    }

    @Override
    public Optional<Consultation> findById(ConsultationId id) {
        return findByKey(id.value());
    }

    @Override
    public List<Consultation> findByInspection(InspectionId inspectionId) {
        return findMany("WHERE t.inspection_id = ?", inspectionId.value());
    }
}
