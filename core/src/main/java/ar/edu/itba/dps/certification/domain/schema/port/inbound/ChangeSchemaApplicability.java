package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

/** Input contract implemented by the application use case. */
public interface ChangeSchemaApplicability {
    InspectionSchema applyTo(SchemaId schemaId, AssetType assetType);

    InspectionSchema stopApplyingTo(SchemaId schemaId, AssetType assetType);

    InspectionSchema transferTo(SchemaId sourceId, SchemaId targetId, AssetType assetType);
}
