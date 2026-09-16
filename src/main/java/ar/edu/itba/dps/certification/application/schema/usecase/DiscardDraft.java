package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

public final class DiscardDraft {

    private final SchemaRepository schemas;
    private final AuditRecorder audit;

    public DiscardDraft(SchemaRepository schemas, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
    }

    public void discard(SchemaId schemaId) {
        InspectionSchema schema = schemas.require(schemaId);
        schema.discardDraft();
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()),
                AuditAction.SCHEMA_DRAFT_DISCARDED,
                AuditDetail.decision("discard the open draft", "discarded"));
    }
}
