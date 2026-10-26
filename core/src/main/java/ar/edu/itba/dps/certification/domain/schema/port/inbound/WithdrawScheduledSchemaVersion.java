package ar.edu.itba.dps.certification.domain.schema.port.inbound;

import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;

/** Input contract implemented by the application use case. */
public interface WithdrawScheduledSchemaVersion {
    InspectionSchema withdraw(SchemaId id, int number, String reason);
}
