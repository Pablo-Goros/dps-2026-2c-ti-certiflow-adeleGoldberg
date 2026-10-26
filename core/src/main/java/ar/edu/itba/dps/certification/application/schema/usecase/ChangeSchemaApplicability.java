package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;

import java.util.Optional;
import java.util.Set;

public final class ChangeSchemaApplicability implements ar.edu.itba.dps.certification.domain.schema.port.inbound.ChangeSchemaApplicability {
    private final Transactions transactions;

    private final SchemaRepository schemas;
    private final SchemaApplicability applicability;
    private final AuditRecorder audit;
    private final Clock clock;

    public ChangeSchemaApplicability(SchemaRepository schemas, SchemaApplicability applicability,
            AuditRecorder audit, Clock clock, Transactions transactions) {
        this.transactions = transactions;
        this.schemas = schemas;
        this.applicability = applicability;
        this.audit = audit;
        this.clock = clock;
    }

    public InspectionSchema applyTo(SchemaId schemaId, AssetType assetType) {
        return transactions.execute(() -> {
            InspectionSchema schema = schemas.require(schemaId);
            Set<AssetType> before = schema.applicableAssetTypes();
            applicability.applyTo(schema, assetType, registeredOwnerOf(assetType), clock.now());
            return audited(schema, before);
        });
    }

    public InspectionSchema stopApplyingTo(SchemaId schemaId, AssetType assetType) {
        return transactions.execute(() -> {
            InspectionSchema schema = schemas.require(schemaId);
            Set<AssetType> before = schema.applicableAssetTypes();
            applicability.stopApplyingTo(schema, assetType);
            return audited(schema, before);
        });
    }

    public InspectionSchema transferTo(SchemaId sourceId, SchemaId targetId, AssetType assetType) {
        return transactions.execute(() -> {
            InspectionSchema source = schemas.require(sourceId);
            InspectionSchema target = schemas.require(targetId);
            Set<AssetType> beforeSource = source.applicableAssetTypes();
            Set<AssetType> beforeTarget = target.applicableAssetTypes();
            applicability.transfer(source, target, assetType, registeredOwnerOf(assetType), clock.now());
            audited(source, beforeSource);
            return audited(target, beforeTarget);
        });
    }

    private Optional<SchemaId> registeredOwnerOf(AssetType assetType) {
        return schemas.findByApplicableAssetType(assetType).map(InspectionSchema::id);
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
