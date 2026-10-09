package ar.edu.itba.dps.certification.application.certification.port;

import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicyRef;
import ar.edu.itba.dps.certification.domain.certification.policy.JurisdictionCertificationPolicy;

public interface CertificationPolicyRegistry {
    JurisdictionCertificationPolicy resolve(JurisdictionId jurisdiction);
    JurisdictionCertificationPolicy historical(CertificationPolicyRef reference);
}
