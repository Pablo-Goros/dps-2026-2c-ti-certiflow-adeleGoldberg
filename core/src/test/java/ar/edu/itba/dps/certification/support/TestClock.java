package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.shared.port.Clock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TestClock implements Clock {

    private Instant now;
    private ZoneId zone = ZoneOffset.UTC;

    public TestClock(Instant start) {
        this.now = start;
    }

    public static TestClock at(String isoInstant) {
        return new TestClock(Instant.parse(isoInstant));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void advanceDays(long days) {
        advance(Duration.ofDays(days));
    }

    public void setNow(Instant instant) {
        now = instant;
    }

    public void useZone(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public LocalDate today() {
        return today(now);
    }

    @Override
    public LocalDate today(Instant at) {
        return at.atZone(zone).toLocalDate();
    }
}
