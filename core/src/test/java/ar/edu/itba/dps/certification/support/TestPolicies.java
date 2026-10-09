package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.adapter.certification.RegisteredCertificationPolicies;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicyRef;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.policy.ConfiguredJurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyRestriction;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.time.Period;
import java.util.Set;

public final class TestPolicies {
    public static final JurisdictionId REFERENCE = JurisdictionId.of("REFERENCE");
    public static CertificationPolicySnapshot reference() {
        return new CertificationPolicySnapshot(new CertificationPolicyRef(REFERENCE, "reference", 1),
                Set.of(), true, Period.ofMonths(12), Period.ofMonths(12),
                Set.of(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED));
    }
    public static RegisteredCertificationPolicies registry() {
        var registry = new RegisteredCertificationPolicies();
        registry.register(REFERENCE, new ConfiguredJurisdictionCertificationPolicy(reference()));
        registry.register(JurisdictionId.of("A"), profile("A", 1, Set.of(Severity.HIGH, Severity.CRITICAL), true, 12, 6));
        registry.register(JurisdictionId.of("B"), profile("B", 1, Set.of(Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL), false, 6, 6));
        return registry;
    }
    public static ConfiguredJurisdictionCertificationPolicy profile(String jurisdiction, int revision,
            Set<Severity> blocking, boolean conditional, int regularMonths, int conditionalMonths) {
        return new ConfiguredJurisdictionCertificationPolicy(new CertificationPolicySnapshot(
                new CertificationPolicyRef(JurisdictionId.of(jurisdiction), jurisdiction, revision),
                blocking, conditional, Period.ofMonths(regularMonths), Period.ofMonths(conditionalMonths), Set.of()));
    }
}
