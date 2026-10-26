package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.SchemaDraft;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.Section;

/** Input contract implemented by the application use case. */
public interface EditDraft {
    SchemaDraft addSection(SchemaId schemaId, Section section);

    SchemaDraft removeSection(SchemaId schemaId, String sectionName);
}
