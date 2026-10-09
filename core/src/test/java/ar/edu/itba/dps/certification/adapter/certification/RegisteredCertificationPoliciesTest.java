package ar.edu.itba.dps.certification.adapter.certification;

import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicyRef;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.policy.JurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyRestriction;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.time.Period;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RegisteredCertificationPoliciesTest {
    @Test void explicitResolutionHistoryAndRegistrationErrors() {
        var registry = TestPolicies.registry();
        var old = registry.resolve(JurisdictionId.of("A"));
        var next = TestPolicies.profile("A", 2, Set.of(Severity.CRITICAL), true, 24, 3);
        registry.register(JurisdictionId.of("A"), next);
        assertThat(registry.resolve(JurisdictionId.of("A")).snapshot()).isEqualTo(next.snapshot());
        assertThat(registry.historical(old.reference()).snapshot()).isEqualTo(old.snapshot());
        assertThatThrownBy(() -> registry.resolve(JurisdictionId.of("unknown"))).isInstanceOf(PolicyResolutionException.class);
        assertThatThrownBy(() -> registry.historical(new CertificationPolicyRef(JurisdictionId.of("A"), "A", 99))).isInstanceOf(PolicyResolutionException.class);
        assertThatThrownBy(() -> registry.register(JurisdictionId.of("A"), next)).isInstanceOf(PolicyResolutionException.class);
        assertThatThrownBy(() -> registry.register(JurisdictionId.of("A"), TestPolicies.profile("A", 1, Set.of(), true, 6, 6))).isInstanceOf(PolicyResolutionException.class);
        assertThatThrownBy(() -> registry.register(JurisdictionId.of("C"), next)).isInstanceOf(PolicyResolutionException.class);
        registry.register(JurisdictionId.of("C"), TestPolicies.profile("C", 1, Set.of(), true, 6, 6));
        assertThat(registry.resolve(JurisdictionId.of("C")).reference().jurisdiction()).isEqualTo(JurisdictionId.of("C"));
    }
    @Test void mutationOfAStrategyDefinitionIsAnExplicitConfigurationFailure() {
        var registry = TestPolicies.registry();
        var strategy = org.mockito.Mockito.mock(JurisdictionCertificationPolicy.class);
        var definition = TestPolicies.profile("C", 1, Set.of(), true, 12, 6).snapshot();
        org.mockito.Mockito.when(strategy.snapshot()).thenReturn(definition);
        org.mockito.Mockito.when(strategy.reference()).thenReturn(definition.reference());
        registry.register(JurisdictionId.of("C"), strategy);
        assertThat(registry.resolve(JurisdictionId.of("C"))).isSameAs(strategy);
        org.mockito.Mockito.when(strategy.snapshot()).thenReturn(TestPolicies.profile("C", 1, Set.of(Severity.CRITICAL), true, 12, 6).snapshot());
        assertThatThrownBy(() -> registry.resolve(JurisdictionId.of("C"))).isInstanceOf(PolicyResolutionException.class).hasMessageContaining("mutated");
        assertThatThrownBy(() -> registry.register(JurisdictionId.of("C"), TestPolicies.profile("C", 1, Set.of(Severity.CRITICAL), true, 12, 6))).isInstanceOf(PolicyResolutionException.class);
    }
    @Test void snapshotDefensivelyCopiesAllSetsAndValidatesPeriods() {
        var severities = EnumSet.of(Severity.HIGH);
        var restrictions = EnumSet.of(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED);
        var ref = new CertificationPolicyRef(JurisdictionId.of("A"), "policy", 1);
        var snapshot = new CertificationPolicySnapshot(ref, severities, true, Period.ofMonths(12), Period.ofMonths(6), restrictions);
        severities.clear(); restrictions.clear();
        assertThat(snapshot.blockingSeverities()).containsExactly(Severity.HIGH);
        assertThat(snapshot.restrictions()).containsExactly(PolicyRestriction.REJECTIONS_MUST_BE_CORRECTED);
        for (var invalid : List.of(Period.ZERO, Period.ofMonths(-1), Period.of(0, 1, -1))) {
            assertThatThrownBy(() -> new CertificationPolicySnapshot(ref, Set.of(), true, invalid, Period.ofMonths(6), Set.of())).hasMessageContaining("positive");
        }
        assertThatThrownBy(() -> new CertificationPolicySnapshot(ref, Set.of(), false, Period.ofMonths(12), Period.ofMonths(6), Set.of())).hasMessageContaining("both modes");
        assertThatThrownBy(() -> new CertificationPolicyRef(JurisdictionId.of("A"), "policy", 0)).hasMessageContaining("revision");
    }
}
