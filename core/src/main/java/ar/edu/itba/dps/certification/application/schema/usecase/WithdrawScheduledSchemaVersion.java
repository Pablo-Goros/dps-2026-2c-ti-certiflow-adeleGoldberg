package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class WithdrawScheduledSchemaVersion implements ar.edu.itba.dps.certification.domain.schema.port.inbound.WithdrawScheduledSchemaVersion {
    private final Transactions transactions;
    private final SchemaRepository schemas;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public WithdrawScheduledSchemaVersion(SchemaRepository schemas, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.schemas = schemas;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public InspectionSchema withdraw(SchemaId id, int number, String reason) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            var schema = schemas.require(id);
            var withdrawal = schema.withdrawScheduledVersion(number, clock.now(), reason);
            schemas.save(schema);
            audit.recordAs(actor, AuditedElementRef.schema(id.value()), AuditAction.SCHEMA_VERSION_WITHDRAWN,
                    AuditDetail.decision("withdraw scheduled version", withdrawal.versionId().toString()), withdrawal.reason());
            return schema;
        });
    }
}
