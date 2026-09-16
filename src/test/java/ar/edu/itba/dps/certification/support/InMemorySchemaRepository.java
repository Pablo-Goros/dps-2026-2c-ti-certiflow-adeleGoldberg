package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySchemaRepository implements SchemaRepository {

    private final Map<SchemaId, InspectionSchema> stored = new LinkedHashMap<>();

    @Override
    public void save(InspectionSchema schema) {
        stored.put(schema.id(), schema);
    }

    @Override
    public Optional<InspectionSchema> findById(SchemaId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<InspectionSchema> findByApplicableAssetType(AssetType assetType) {
        return stored.values().stream().filter(schema -> schema.appliesTo(assetType)).findFirst();
    }

    @Override
    public List<InspectionSchema> findAll() {
        return new ArrayList<>(stored.values());
    }
}
