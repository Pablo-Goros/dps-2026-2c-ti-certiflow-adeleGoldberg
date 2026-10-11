package ar.edu.itba.dps.certification.application.shared.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public interface Clock {

    Instant now();

    LocalDate today();

    /** The business calendar date for the same instant used by the operation. */
    default LocalDate today(Instant at) {
        return at.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
