package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;

import java.time.Instant;
import java.util.List;

/**
 * Pure immutable strategy. The snapshot must completely describe its rules, including every
 * named restriction, so lifecycle decisions can interpret the stored definition without a registry.
 */
public interface JurisdictionCertificationPolicy {
    CertificationPolicySnapshot snapshot();
    default CertificationPolicyRef reference() { return snapshot().reference(); }
    List<IssuanceBlocker> complianceBlockers(CertificationContext context);
    CertificationAssessment assess(CertificationContext context, Instant at);
    ValidityPeriod validityFrom(Instant at, CertificateMode mode);
}
