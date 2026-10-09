package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcInspectionRepository extends DocumentRepository<Inspection> implements InspectionRepository {

    private static final String CLOSED = InspectionStatus.CLOSED.name();

    public JdbcInspectionRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Inspection.class, "inspection");
    }

    @Override
    public void save(Inspection inspection) {
        Map<String, Object> indexed = columns();
        indexed.put("asset_id", inspection.assetId().value());
        indexed.put("status", inspection.status().name());
        store(inspection.id().value(), inspection, indexed);
    }

    @Override
    public Optional<Inspection> findById(InspectionId id) {
        return findByKey(id.value());
    }

    @Override
    public InspectionSummary summaryOf(InspectionId id) {
        return InspectionSummary.of(require(id));
    }

    @Override
    public boolean wasRectified(InspectionId id) {
        return !require(id).rectifications().isEmpty();
    }

    @Override
    public Optional<Inspection> findNonClosedByAsset(AssetId assetId) {
        return findOne("WHERE t.asset_id = ? AND t.status <> ?", assetId.value(), CLOSED);
    }

    @Override
    public List<Inspection> findClosedByAsset(AssetId assetId) {
        return findMany("WHERE t.asset_id = ? AND t.status = ?", assetId.value(), CLOSED);
    }

    @Override
    public List<Inspection> findAll() {
        return findMany("");
    }
}
