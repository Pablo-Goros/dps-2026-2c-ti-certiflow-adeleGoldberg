package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Set;

public final class CreateSchema {

    private final SchemaRepository schemas;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public CreateSchema(SchemaRepository schemas, IdGenerator ids, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
        this.ids = ids;
    }

    public InspectionSchema create(String name, Set<AssetType> applicableAssetTypes) {
        Validate.requiredNonEmpty(applicableAssetTypes, "applicable asset types");
        for (AssetType assetType : applicableAssetTypes) {
            schemas.findByApplicableAssetType(assetType).ifPresent(existing -> {
                throw new DomainException(
                        "asset type " + assetType + " is already covered by schema " + existing.id());
            });
        }
        InspectionSchema schema = new InspectionSchema(new SchemaId(ids.newIdentifier()), name,
                applicableAssetTypes);
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()), AuditAction.SCHEMA_CREATED,
                AuditDetail.created("schema '" + schema.name() + "' for " + applicableAssetTypes));
        return schema;
    }
}
