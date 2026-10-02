package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;

import java.util.Set;

public final class CreateSchema {

    private final SchemaRepository schemas;
    private final SchemaApplicability applicability;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public CreateSchema(SchemaRepository schemas, SchemaApplicability applicability, IdGenerator ids,
            AuditRecorder audit) {
        this.schemas = schemas;
        this.applicability = applicability;
        this.audit = audit;
        this.ids = ids;
    }

    public InspectionSchema create(String name, Set<AssetType> applicableAssetTypes) {
        InspectionSchema schema = applicability.create(new SchemaId(ids.newIdentifier()), name, applicableAssetTypes);
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()), AuditAction.SCHEMA_CREATED,
                AuditDetail.created("schema '" + schema.name() + "' for " + applicableAssetTypes));
        return schema;
    }
}
