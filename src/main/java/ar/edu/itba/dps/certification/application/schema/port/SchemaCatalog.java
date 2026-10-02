package ar.edu.itba.dps.certification.application.schema.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;

import java.util.Optional;

public interface SchemaCatalog {

    Optional<SchemaVersion> latestPublishedVersionFor(AssetType assetType);

    SchemaVersion requireVersion(SchemaVersionId versionId);
}
