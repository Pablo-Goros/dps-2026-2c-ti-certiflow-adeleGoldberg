package ar.edu.itba.dps.certification.application.schema.usecase;

import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read side of the inspection schemas, including F2's "which version was in force on this date". */
public final class BrowseSchemas {

    private final SchemaRepository schemas;

    public BrowseSchemas(SchemaRepository schemas) {
        this.schemas = schemas;
    }

    public List<InspectionSchema> all() {
        return schemas.findAll();
    }

    public Optional<InspectionSchema> find(SchemaId id) {
        return schemas.findById(id);
    }

    /** The version of a schema by number, empty if the schema or the version does not exist. */
    public Optional<SchemaVersion> version(SchemaId id, int number) {
        return schemas.findById(id).flatMap(schema -> schema.findVersion(number));
    }

    /**
     * The version that was (or will be) in force at {@code moment}: the latest one whose effective
     * date is not after it. Empty if the schema does not exist or nothing was in force yet.
     */
    public Optional<SchemaVersion> versionInForceAt(SchemaId id, Instant moment) {
        return schemas.findById(id).flatMap(schema -> schema.effectiveVersionAt(moment));
    }
}
