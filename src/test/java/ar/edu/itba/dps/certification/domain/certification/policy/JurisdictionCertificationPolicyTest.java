package ar.edu.itba.dps.certification.domain.certification.policy;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static org.assertj.core.api.Assertions.*;

class JurisdictionCertificationPolicyTest {
    static Stream<Arguments> profiles() {
        return Arrays.stream(Severity.values()).flatMap(severity -> Stream.of(CriterionResult.OBSERVED, CriterionResult.REJECTED)
                .flatMap(result -> Stream.of(true, false).flatMap(blocking -> Stream.of(true, false)
                        .map(conditional -> Arguments.of(severity, result, blocking, conditional)))));
    }
    @ParameterizedTest @MethodSource("profiles")
    void severityAndConditionalMatrix(Severity severity, CriterionResult result, boolean blocking, boolean conditional) {
        var facts = new PolicyFacts(result, severity); facts.plan();
        var policy = TestPolicies.profile("A", 1, blocking ? Set.of(severity) : Set.of(), conditional, 12, conditional ? 6 : 12);
        var assessment = policy.assess(facts.context(), PolicyFacts.AT);
        assertThat(assessment.mode().isPresent()).isEqualTo(!blocking && conditional);
        assertThat(assessment.blockers().stream().anyMatch(IssuanceBlocker.BlockingSeverity.class::isInstance)).isEqualTo(blocking);
        assertThat(assessment.blockers().stream().anyMatch(IssuanceBlocker.ConditionalNotAllowed.class::isInstance)).isEqualTo(!conditional);
        assertThat(assessment.policy()).isEqualTo(policy.snapshot());
    }
    @Test void approvedAndVerifiedCorrectionsAreRegularButExecutionAloneIsPending() {
        var policy = TestPolicies.profile("A", 1, Set.of(Severity.HIGH), true, 12, 6);
        var approved = new PolicyFacts(CriterionResult.APPROVED, null);
        assertThat(policy.assess(approved.context(), PolicyFacts.AT).mode()).contains(CertificateMode.REGULAR);
        var facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.HIGH); facts.plan();
        facts.finding.reportCorrectionExecution(new ExecutionReport(
                "done", List.of("proof"), PolicyFacts.OWNER, PolicyFacts.AT));
        assertThat(policy.assess(facts.context(), PolicyFacts.AT).blockers()).anyMatch(IssuanceBlocker.BlockingSeverity.class::isInstance);
        facts.finding.concludeCorrection(Verification.satisfactory(
                "verified", PolicyFacts.INSPECTOR, PolicyFacts.AT), PolicyFacts.today());
        assertThat(policy.assess(facts.context(), PolicyFacts.AT).mode()).contains(CertificateMode.REGULAR);
        facts.finding.revise(CriterionResult.OBSERVED, facts.finding.reasons(), Severity.HIGH, List.of(),
                RectificationId.of("rect"), "recurring", PolicyFacts.AT, CorrectiveActionId.of("new-action"));
        assertThat(policy.assess(facts.context(), PolicyFacts.AT).blockers()).anyMatch(IssuanceBlocker.BlockingSeverity.class::isInstance);
    }
    @Test void missingUnplannedOverdueAndProhibitedAccumulateAndDatesAreInclusive() {
        var facts = new PolicyFacts(CriterionResult.REJECTED, Severity.CRITICAL);
        var policy = TestPolicies.profile("A", 1, Set.of(Severity.CRITICAL), false, 6, 6);
        assertThat(policy.assess(facts.context(PolicyFacts.today(), List.of()), PolicyFacts.AT).blockers()).hasSize(3);
        assertThat(policy.assess(facts.context(), PolicyFacts.AT).blockers()).anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance);
        facts.plan();
        assertThat(facts.context().overdueOpenActions()).isZero();
        assertThat(policy.assess(facts.context(PolicyFacts.today().plusDays(1), List.of(facts.finding)), PolicyFacts.AT).blockers()).hasSize(3);
        facts.finding.voidObligation(RectificationId.of("void"), "removed", PolicyFacts.AT);
        assertThat(policy.assess(facts.context(), PolicyFacts.AT).mode()).contains(CertificateMode.REGULAR);
    }
    @Test void complianceDoesNotReapplyRulesOfInitialIssuance() {
        var facts = new PolicyFacts(CriterionResult.APPROVED, null);
        var policy = TestPolicies.profile("A", 1, Set.of(), true, 12, 6);
        var context = new CertificationContext(facts.inspection, List.of(), PolicyFacts.today(), CertificateScope.global(),
                Optional.of(ar.edu.itba.dps.certification.domain.inspection.InspectionId.of("later")), Optional.of(CertificateId.of("live")));
        assertThat(policy.assess(context, PolicyFacts.AT).blockers()).hasSize(2);
        assertThat(policy.complianceBlockers(context)).isEmpty();
        var pending = new PolicyFacts(CriterionResult.REJECTED, Severity.CRITICAL);
        assertThat(policy.complianceBlockers(pending.context())).anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance);
    }
    @Test void forbiddenConditionalModeAndUnknownFieldsAreRejected() {
        var policy = TestPolicies.profile("B", 1, Set.of(), false, 6, 6);
        assertThatThrownBy(() -> policy.validityFrom(PolicyFacts.AT, CertificateMode.CONDITIONAL)).hasMessageContaining("forbidden");
        assertThat(policy.snapshot().permitsPending(CriterionResult.OBSERVED, Severity.LOW)).isFalse();
        assertThatThrownBy(() -> new CertificationPolicyRef(null, "id", 1)).hasMessageContaining("jurisdiction");
        assertThatThrownBy(() -> new CertificationPolicyRef(TestPolicies.REFERENCE, "  ", 1)).hasMessageContaining("policy id");
    }
    @Test void differentModesUseUtcCalendarDurationsIncludingLeapYears() {
        var policy = TestPolicies.profile("A", 1, Set.of(), true, 12, 6);
        var leap = Instant.parse("2024-02-29T10:00:00Z");
        assertThat(policy.validityFrom(leap, CertificateMode.REGULAR).expiresAt()).isEqualTo(Instant.parse("2025-02-28T10:00:00Z"));
        assertThat(policy.validityFrom(leap, CertificateMode.CONDITIONAL).expiresAt()).isEqualTo(Instant.parse("2024-08-29T10:00:00Z"));
        var validity = policy.validityFrom(leap, CertificateMode.REGULAR);
        assertThat(validity.coversMoment(validity.expiresAt().minusNanos(1))).isTrue();
        assertThat(validity.coversMoment(validity.expiresAt())).isFalse();
    }
}
