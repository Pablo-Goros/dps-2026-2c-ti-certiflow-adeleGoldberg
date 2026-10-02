package ar.edu.itba.dps.certification.application.shared.port;

import java.time.Instant;
import java.time.LocalDate;

public interface Clock {

    Instant now();

    LocalDate today();
}
