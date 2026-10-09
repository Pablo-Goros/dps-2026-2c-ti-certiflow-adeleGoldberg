package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.adapter.certification.RegisteredCertificationPolicies;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicyRef;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.policy.ConfiguredJurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyRestriction;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Period;
import java.util.List;
import java.util.Set;

/**
 * Certification profiles per jurisdiction (F3). They are code-level configuration on purpose:
 * adding one is a new line here, never a change to the certificate factory.
 */
@Configuration(proxyBeanMethods = false)
class PolicyConfig {

    /** Jurisdictions the API accepts for new assets, in display order. */
    static final List<String> JURISDICTIONS = List.of("REFERENCE", "AR-BA", "AR-CBA");

    @Bean
    RegisteredCertificationPolicies certificationPolicies() {
        var registry = new RegisteredCertificationPolicies();
        registry.register(JurisdictionId.of("REFERENCE"), profile("REFERENCE", "reference", 1,
                Set.of(), true, 12, 12, Set.of(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED)));
        registry.register(JurisdictionId.of("AR-BA"), profile("AR-BA", "Buenos Aires", 1,
                Set.of(Severity.HIGH, Severity.CRITICAL), true, 12, 6, Set.of()));
        registry.register(JurisdictionId.of("AR-CBA"), profile("AR-CBA", "Córdoba", 1,
                Set.of(Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL), false, 6, 6, Set.of()));
        return registry;
    }

    @Bean
    JurisdictionCatalog jurisdictionCatalog() {
        return new JurisdictionCatalog(JURISDICTIONS);
    }

    private static ConfiguredJurisdictionCertificationPolicy profile(String jurisdiction, String name,
            int revision, Set<Severity> blocking, boolean conditional, int regularMonths,
            int conditionalMonths, Set<PolicyRestriction> restrictions) {
        return new ConfiguredJurisdictionCertificationPolicy(new CertificationPolicySnapshot(
                new CertificationPolicyRef(JurisdictionId.of(jurisdiction), name, revision),
                blocking, conditional, Period.ofMonths(regularMonths),
                Period.ofMonths(conditionalMonths), restrictions));
    }
}
