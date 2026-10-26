package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseSchemas {
    List<InspectionSchema> all();

    Optional<InspectionSchema> find(SchemaId id);

    Optional<SchemaVersion> version(SchemaId id, int number);

    Optional<SchemaVersion> versionInForceAt(SchemaId id, Instant moment);
}
