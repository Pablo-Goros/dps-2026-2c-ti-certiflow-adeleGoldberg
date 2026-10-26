package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class DiscardDraft implements ar.edu.itba.dps.certification.domain.schema.port.inbound.DiscardDraft {
    private final Transactions transactions;

    private final SchemaRepository schemas;
    private final AuditRecorder audit;

    public DiscardDraft(SchemaRepository schemas, AuditRecorder audit, Transactions transactions) {
        this.transactions = transactions;
        this.schemas = schemas;
        this.audit = audit;
    }

    public void discard(SchemaId schemaId) {
        transactions.execute(() -> {
            InspectionSchema schema = schemas.require(schemaId);
            schema.discardDraft();
            schemas.save(schema);
            audit.record(AuditedElementRef.schema(schema.id().value()),
                    AuditAction.SCHEMA_DRAFT_DISCARDED,
                    AuditDetail.decision("discard the open draft", "discarded"));
        });
    }
}
