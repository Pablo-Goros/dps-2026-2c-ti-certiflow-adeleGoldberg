package ar.edu.itba.dps.certification.domain.certification.issuance;

import java.util.Optional;

public interface IssuanceRequirement {

    Optional<IssuanceBlocker> unmetBy(CertificationContext context);
}
