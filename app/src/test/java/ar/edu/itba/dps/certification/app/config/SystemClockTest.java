package ar.edu.itba.dps.certification.app.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SystemClockTest {
    @Test void explicitBusinessDatesUseBuenosAiresEvenWhenUtcHasAlreadyChangedDay() {
        var clock = new SystemClock(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(clock.today(Instant.parse("2026-03-05T13:00:00Z"))).isEqualTo(LocalDate.parse("2026-03-05"));
        assertThat(clock.today(Instant.parse("2026-03-06T01:30:00Z"))).isEqualTo(LocalDate.parse("2026-03-05"));
        assertThat(clock.today(Instant.parse("2026-03-06T03:00:00Z"))).isEqualTo(LocalDate.parse("2026-03-06"));
    }
}
