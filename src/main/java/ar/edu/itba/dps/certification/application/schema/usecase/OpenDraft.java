package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaDraft;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

public final class OpenDraft {

    private final SchemaRepository schemas;
    private final AuditRecorder audit;

    public OpenDraft(SchemaRepository schemas, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
    }

    public SchemaDraft open(SchemaId schemaId) {
        InspectionSchema schema = schemas.require(schemaId);
        SchemaDraft draft = schema.openDraft();
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()), AuditAction.SCHEMA_DRAFT_OPENED,
                AuditDetail.created("draft seeded from " + schema.latestPublishedVersion()
                        .map(version -> "version " + version.number()).orElse("an empty schema")));
        return draft;
    }
}
