package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record SchemaId(String value) {

    public SchemaId {
        value = Validate.requiredText(value, "schema id");
    }

    public static SchemaId of(String value) {
        return new SchemaId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
