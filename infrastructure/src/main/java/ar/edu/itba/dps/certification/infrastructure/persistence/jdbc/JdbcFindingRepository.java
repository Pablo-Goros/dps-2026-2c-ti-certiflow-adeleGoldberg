package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcFindingRepository extends DocumentRepository<Finding> implements FindingRepository {

    public JdbcFindingRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Finding.class, "finding");
    }

    @Override
    public void save(Finding finding) {
        Map<String, Object> indexed = columns();
        indexed.put("inspection_id", finding.inspectionId().value());
        indexed.put("criterion_id", finding.criterionId().value());
        indexed.put("action_open", finding.correctiveAction().status().open());
        store(finding.id().value(), finding, indexed);
    }

    @Override
    public Optional<Finding> findById(FindingId id) {
        return findByKey(id.value());
    }

    @Override
    public Optional<Finding> findByCriterion(InspectionId inspectionId, CriterionId criterionId) {
        return findOne("WHERE t.inspection_id = ? AND t.criterion_id = ?",
                inspectionId.value(), criterionId.value());
    }

    @Override
    public List<Finding> findByInspection(InspectionId inspectionId) {
        return findMany("WHERE t.inspection_id = ?", inspectionId.value());
    }

    @Override
    public List<Finding> findWithOpenActions() {
        return findMany("WHERE t.action_open = ?", true);
    }

    @Override
    public List<Finding> findAll() {
        return findMany("");
    }
}
