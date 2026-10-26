package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

import java.time.Instant;

/** Input contract implemented by the application use case. */
public interface PublishSchemaVersion {
    PublicationResult publish(SchemaId schemaId);

    PublicationResult publish(SchemaId schemaId, Instant effectiveFrom);
}
