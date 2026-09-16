package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

import java.util.Set;

public final class ChangeSchemaApplicability {

    private final SchemaRepository schemas;
    private final AuditRecorder audit;

    public ChangeSchemaApplicability(SchemaRepository schemas, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
    }

    public InspectionSchema applyTo(SchemaId schemaId, AssetType assetType) {
        InspectionSchema schema = schemas.require(schemaId);
        schemas.findByApplicableAssetType(assetType).ifPresent(existing -> {
            if (!existing.id().equals(schemaId)) {
                throw new DomainException("asset type " + assetType
                        + " is already covered by schema " + existing.id());
            }
        });
        Set<AssetType> before = schema.applicableAssetTypes();
        schema.applyTo(assetType);
        return audited(schema, before);
    }

    public InspectionSchema stopApplyingTo(SchemaId schemaId, AssetType assetType) {
        InspectionSchema schema = schemas.require(schemaId);
        Set<AssetType> before = schema.applicableAssetTypes();
        schema.stopApplyingTo(assetType);
        return audited(schema, before);
    }

    private InspectionSchema audited(InspectionSchema schema, Set<AssetType> before) {
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()),
                AuditAction.SCHEMA_APPLICABILITY_CHANGED,
                AuditDetail.dataChanged(FieldChange.of("applicableAssetTypes", before,
                        schema.applicableAssetTypes())));
        return schema;
    }
}
