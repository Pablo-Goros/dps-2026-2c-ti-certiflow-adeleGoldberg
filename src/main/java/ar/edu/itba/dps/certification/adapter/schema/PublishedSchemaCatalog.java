package ar.edu.itba.dps.certification.adapter.schema;

import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

public final class PublishedSchemaCatalog implements SchemaCatalog {

    private final SchemaRepository schemas;

    public PublishedSchemaCatalog(SchemaRepository schemas) {
        this.schemas = schemas;
    }

    @Override
    public Optional<SchemaVersion> effectiveVersionFor(AssetType assetType, Instant at) {
        Validate.required(assetType, "asset type");
        Validate.required(at, "evaluation instant");
        return schemas.findByApplicableAssetType(assetType)
                .flatMap(schema -> schema.effectiveVersionAt(at));
    }

    @Override
    public Optional<SchemaVersion> latestPublishedVersionFor(AssetType assetType) {
        return schemas.findByApplicableAssetType(assetType)
                .flatMap(InspectionSchema::latestPublishedVersion);
    }

    @Override
    public SchemaVersion requireVersion(SchemaVersionId versionId) {
        Validate.required(versionId, "schema version id");
        return schemas.require(versionId.schemaId())
                .findVersion(versionId)
                .orElseThrow(() -> new DomainException("schema version " + versionId + " does not exist"));
    }
}
