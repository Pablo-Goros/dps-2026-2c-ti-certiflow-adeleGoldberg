package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Optional;

/**
 * Stores each schema (with its draft and published versions) as one document. The asset types a
 * schema applies to are also written to {@code schema_applicability}, whose primary key is the
 * asset type: the exclusivity rule ("one schema per type") is therefore backed by the database,
 * not only by the domain check.
 */
public final class JdbcSchemaRepository extends DocumentRepository<InspectionSchema> implements SchemaRepository {

    public JdbcSchemaRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, InspectionSchema.class, "inspection_schema");
    }

    @Override
    public void save(InspectionSchema schema) {
        db().execute(() -> {
            String id = schema.id().value();
            store(id, schema, columns());
            db().update("DELETE FROM schema_applicability WHERE schema_id = ?", id);
            for (AssetType type : schema.applicableAssetTypes()) {
                db().update("INSERT INTO schema_applicability (asset_type, schema_id) VALUES (?, ?)",
                        type.name(), id);
            }
        });
    }

    @Override
    public Optional<InspectionSchema> findById(SchemaId id) {
        return findByKey(id.value());
    }

    @Override
    public Optional<InspectionSchema> findByApplicableAssetType(AssetType assetType) {
        return findOne("JOIN schema_applicability a ON a.schema_id = t.id WHERE a.asset_type = ?",
                assetType.name());
    }

    @Override
    public List<InspectionSchema> findAll() {
        return findMany("");
    }
}
