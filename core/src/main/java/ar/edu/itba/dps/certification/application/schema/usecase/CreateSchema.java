package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;

import java.util.Set;
import java.util.stream.Collectors;

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
        Set<AssetType> alreadyCovered = applicableAssetTypes.stream()
                .filter(type -> schemas.findByApplicableAssetType(type).isPresent())
                .collect(Collectors.toSet());
        InspectionSchema schema = applicability.create(new SchemaId(ids.newIdentifier()), name,
                applicableAssetTypes, alreadyCovered);
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()), AuditAction.SCHEMA_CREATED,
                AuditDetail.created("schema '" + schema.name() + "' for " + applicableAssetTypes));
        return schema;
    }
}
