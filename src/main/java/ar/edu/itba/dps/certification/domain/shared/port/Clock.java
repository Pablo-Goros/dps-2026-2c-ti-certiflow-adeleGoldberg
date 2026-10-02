package ar.edu.itba.dps.certification.domain.shared.port;

import java.time.Instant;
import java.time.LocalDate;

public interface Clock {

    Instant now();

    LocalDate today();
}
