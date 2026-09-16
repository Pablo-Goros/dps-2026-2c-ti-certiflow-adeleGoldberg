package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

public final class PublishSchemaVersion {

    private final SchemaRepository schemas;
    private final Clock clock;
    private final AuditRecorder audit;

    public PublishSchemaVersion(SchemaRepository schemas, Clock clock, AuditRecorder audit) {
        this.schemas = schemas;
        this.audit = audit;
        this.clock = clock;
    }

    public PublicationResult publish(SchemaId schemaId) {
        InspectionSchema schema = schemas.require(schemaId);
        PublicationResult result = schema.publish(clock.now());
        schemas.save(schema);
        audit.record(AuditedElementRef.schema(schema.id().value()),
                AuditAction.SCHEMA_VERSION_PUBLISHED,
                AuditDetail.decision("publish the draft", result.published()
                        ? "published version " + result.publishedVersion().number()
                        : "refused: " + String.join("; ", result.violations())));
        return result;
    }
}
