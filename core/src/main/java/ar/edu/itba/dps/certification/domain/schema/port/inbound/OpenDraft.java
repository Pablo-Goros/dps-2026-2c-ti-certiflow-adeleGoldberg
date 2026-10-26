package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.SchemaDraft;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

/** Input contract implemented by the application use case. */
public interface OpenDraft {
    SchemaDraft open(SchemaId schemaId);
}
