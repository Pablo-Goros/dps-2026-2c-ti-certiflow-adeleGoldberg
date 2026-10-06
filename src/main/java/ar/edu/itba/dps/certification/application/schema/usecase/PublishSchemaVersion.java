package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;

import java.time.Instant;

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
        return publish(schemaId, clock.now());
    }

    public PublicationResult publish(SchemaId schemaId, Instant effectiveFrom) {
        InspectionSchema schema = schemas.require(schemaId);
        Instant now = clock.now();
        PublicationResult result = schema.publish(now, effectiveFrom);
        if (!result.published()) {
            // A refused draft stays open and unchanged: there is nothing to save, but the attempt is
            // a decision worth auditing, and it must not read as a publication.
            audit.record(AuditedElementRef.schema(schema.id().value()),
                    AuditAction.SCHEMA_PUBLICATION_REFUSED,
                    AuditDetail.decision("publish the draft", "refused: " + String.join("; ", result.violations())));
            return result;
        }
        schemas.save(schema);
        String detailText = "published version " + result.publishedVersion().number()
                + (effectiveFrom.isAfter(now) ? " (effective from " + effectiveFrom + ")" : "");
        audit.record(AuditedElementRef.schema(schema.id().value()),
                AuditAction.SCHEMA_VERSION_PUBLISHED,
                AuditDetail.decision("publish the draft", detailText));
        return result;
    }
}
