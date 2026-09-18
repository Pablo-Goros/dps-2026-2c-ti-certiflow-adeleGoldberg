package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;

public record ValidityPeriod(Instant issuedAt, Instant expiresAt) {

    public ValidityPeriod {
        Validate.required(issuedAt, "issue instant");
        Validate.required(expiresAt, "expiry instant");
        Validate.ensure(expiresAt.isAfter(issuedAt), "a certificate must expire after it is issued");
    }

    public boolean expiredAt(Instant moment) {
        return !moment.isBefore(expiresAt);
    }

    public boolean coversMoment(Instant moment) {
        return !moment.isBefore(issuedAt) && !expiredAt(moment);
    }
}
