package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

public final class FixedDurationValidityPolicy implements CertificateValidityPolicy {

    private final Period duration;

    public FixedDurationValidityPolicy(Period duration) {
        this.duration = Validate.required(duration, "validity duration");
    }

    public static FixedDurationValidityPolicy ofMonths(int months) {
        return new FixedDurationValidityPolicy(Period.ofMonths(months));
    }

    @Override
    public ValidityPeriod validityFrom(Instant issuedAt) {
        Instant expiresAt = issuedAt.atZone(ZoneOffset.UTC).plus(duration).toInstant();
        return new ValidityPeriod(issuedAt, expiresAt);
    }
}
