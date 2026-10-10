package ar.edu.itba.dps.certification.app;

import ar.edu.itba.dps.certification.application.shared.port.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Real time plus an offset the test can move forward; it replaces the application clock when imported. */
final class SteerableClock implements Clock {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        SteerableClock steerableClock() {
            return new SteerableClock();
        }
    }

    private final ZoneId zone = ZoneId.of("America/Argentina/Buenos_Aires");
    private volatile Duration offset = Duration.ZERO;

    void advanceDays(long days) {
        offset = offset.plusDays(days);
    }

    void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public Instant now() {
        return Instant.now().plus(offset);
    }

    @Override
    public LocalDate today() {
        return LocalDate.ofInstant(now(), zone);
    }
}
