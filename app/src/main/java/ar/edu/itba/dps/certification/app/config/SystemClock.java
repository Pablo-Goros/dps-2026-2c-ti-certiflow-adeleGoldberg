package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.application.shared.port.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SystemClock implements Clock {

    private final ZoneId zone;

    public SystemClock(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(zone);
    }
}
