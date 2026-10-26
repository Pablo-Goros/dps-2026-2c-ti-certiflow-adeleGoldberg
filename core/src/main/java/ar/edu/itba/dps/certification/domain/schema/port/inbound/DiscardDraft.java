package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.SchemaId;

/** Input contract implemented by the application use case. */
public interface DiscardDraft {
    void discard(SchemaId schemaId);
}
