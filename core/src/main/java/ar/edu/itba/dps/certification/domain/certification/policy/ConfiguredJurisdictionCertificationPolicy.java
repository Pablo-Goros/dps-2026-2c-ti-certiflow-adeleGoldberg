package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;

public final class ConfiguredJurisdictionCertificationPolicy implements JurisdictionCertificationPolicy {
    private final CertificationPolicySnapshot snapshot;
    public ConfiguredJurisdictionCertificationPolicy(CertificationPolicySnapshot snapshot) {
        this.snapshot = Validate.required(snapshot, "policy snapshot");
    }
    @Override
    public CertificationPolicySnapshot snapshot() {
        return snapshot;
    }

    @Override
    public List<IssuanceBlocker> complianceBlockers(CertificationContext context) {
        return snapshot.complianceBlockers(context);
    }

    @Override
    public CertificationAssessment assess(CertificationContext context, Instant at) {
        return CertificationAssessment.evaluate(context, at, snapshot);
    }

    @Override
    public ValidityPeriod validityFrom(Instant at, CertificateMode mode) {
        return snapshot.validityFrom(at, mode);
    }
}
