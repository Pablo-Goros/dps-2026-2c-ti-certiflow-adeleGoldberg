package ar.edu.itba.dps.certification.domain.certification;

import java.time.Instant;

public interface CertificateValidityPolicy {

    ValidityPeriod validityFrom(Instant issuedAt);
}
