package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PolicyAwareCertificateLifecycleTest {
    private Certificate certificate(PolicyFacts facts, CertificateMode mode, Set<Severity> blocking) {
        var policy = TestPolicies.profile("REFERENCE", 1, blocking, true, 12, 6);
        return new CertificateIssuer().issue(CertificateId.of("certificate"), facts.inspection.assetId(), facts.inspection.id(),
                facts.inspection.requireFrozenSchemaVersionId(), CertificateScope.global(), policy.validityFrom(PolicyFacts.AT, mode),
                null, policy.snapshot(), mode);
    }
    @Test void aPlannedPermittedRejectionRemainsConditionalAndRepeatedFactsDoNotAuditAgain() {
        var facts = new PolicyFacts(CriterionResult.REJECTED, Severity.MEDIUM); facts.plan();
        var c = certificate(facts, CertificateMode.CONDITIONAL, Set.of(Severity.CRITICAL));
        var lifecycle = new CertificateLifecycle();
        assertThat(lifecycle.reconcile(c, facts.context(), PolicyFacts.AT)).isEmpty();
        assertThat(c.status()).isEqualTo(CertificateStatus.VALID);
        var regular = certificate(facts, CertificateMode.REGULAR, Set.of());
        assertThat(lifecycle.reconcile(regular, facts.context(), PolicyFacts.AT)).isPresent();
        assertThat(lifecycle.reconcile(regular, facts.context(), PolicyFacts.AT)).isEmpty();
        assertThat(regular.status()).isEqualTo(CertificateStatus.SUSPENDED);
    }
    @Test void verificationResolvesObservedBlockingSeverityWithoutChangingModeOrValidity() {
        var facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.HIGH); facts.plan();
        var c = certificate(facts, CertificateMode.CONDITIONAL, Set.of(Severity.HIGH));
        var lifecycle = new CertificateLifecycle(); lifecycle.reconcile(c, facts.context(), PolicyFacts.AT);
        assertThat(c.status()).isEqualTo(CertificateStatus.SUSPENDED);
        facts.verify();
        assertThat(lifecycle.reconcile(c, facts.context(), PolicyFacts.AT).orElseThrow().reactivated()).isTrue();
        assertThat(c.mode()).isEqualTo(CertificateMode.CONDITIONAL);
        assertThat(c.policy().reference().revision()).isEqualTo(1);
        assertThat(c.suspensions()).singleElement().satisfies(record -> assertThat(record.unresolved()).isFalse());
    }
    @Test void absentFindingDoesNotHideViolationAndExpiryPreventsReactivation() {
        var facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW);
        var c = certificate(facts, CertificateMode.CONDITIONAL, Set.of());
        var lifecycle = new CertificateLifecycle();
        lifecycle.reconcile(c, facts.context(PolicyFacts.today(), List.of()), PolicyFacts.AT);
        assertThat(c.status()).isEqualTo(CertificateStatus.SUSPENDED);
        facts.finding.voidObligation(RectificationId.of("void"), "removed", PolicyFacts.AT);
        lifecycle.reconcile(c, facts.context(), c.validity().expiresAt());
        assertThat(c.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(c.unresolvedCauses()).isEmpty();
        assertThat(c.inForceAt(c.validity().expiresAt())).isFalse();
    }
}
