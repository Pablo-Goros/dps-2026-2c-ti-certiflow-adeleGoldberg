package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record SchemaVersionId(SchemaId schemaId, int number) {

    public SchemaVersionId {
        Validate.required(schemaId, "schema id");
        Validate.requiredPositive(number, "schema version number");
    }

    @Override
    public String toString() {
        return schemaId + "#v" + number;
    }
}
