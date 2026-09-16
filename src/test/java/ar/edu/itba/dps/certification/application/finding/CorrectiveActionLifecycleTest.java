package ar.edu.itba.dps.certification.application.finding;

import ar.edu.itba.dps.certification.support.FixedActor;
import ar.edu.itba.dps.certification.support.InMemoryAuditTrail;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.support.InMemoryFindingRepository;
import ar.edu.itba.dps.certification.support.RecordingEventPublisher;
import ar.edu.itba.dps.certification.support.TestClock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;
import java.util.List;

class CorrectiveActionLifecycleTest {

    private static final LocalDate DUE_DATE = LocalDate.parse("2026-03-10");
    private static final PartyId EXECUTOR = PartyId.of("executor");
    private static final PartyId INSPECTOR = PartyId.of("inspector");

    private TestClock clock;
    private InMemoryFindingRepository findings;
    private RecordingEventPublisher events;
    private PlanCorrectiveAction plan;
    private ReportCorrectiveActionExecution reportExecution;
    private VerifyCorrectiveAction verify;
    private ExpireOverdueCorrectiveActions expire;
    private FindingId findingId;

    @BeforeEach
    void setUp() {
        clock = TestClock.at("2026-03-01T09:00:00Z");
        findings = new InMemoryFindingRepository();
        AuditRecorder audit = new AuditRecorder(new InMemoryAuditTrail(),
                new FixedActor("inspector"), clock);
        events = new RecordingEventPublisher();
        plan = new PlanCorrectiveAction(findings, audit);
        reportExecution = new ReportCorrectiveActionExecution(findings, clock, audit);
        verify = new VerifyCorrectiveAction(findings, clock, events, audit);
        expire = new ExpireOverdueCorrectiveActions(findings, clock, events, audit);
        findingId = givenAFinding();
    }

    @Test
    @DisplayName("a confirmed plan cannot be modified")
    void aConfirmedPlanIsImmutable() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);

        assertThatThrownBy(() -> plan.plan(findingId, "something else", EXECUTOR,
                DUE_DATE.plusDays(30)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already been planned");
    }

    @Test
    @DisplayName("closing on the due date meets the deadline")
    void closingOnTheDueDateMeetsTheDeadline() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        clock.advanceDays(9);
        reportExecution.report(findingId, "thermostat replaced", List.of("file://photo.jpg"),
                EXECUTOR);

        Finding finding = verify.verify(findingId, true, "measured within range", INSPECTOR);

        assertThat(clock.today()).isEqualTo(DUE_DATE);
        assertThat(finding.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.CLOSED);
        assertThat(finding.correctiveAction().deadlineBreached()).isFalse();
        assertThat(finding.correctiveAction().metItsDeadline()).isTrue();
    }

    @Test
    @DisplayName("reporting the execution before the deadline is not enough on its own")
    void reportingExecutionIsNotEnough() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        clock.advanceDays(5);
        reportExecution.report(findingId, "thermostat replaced", List.of("file://photo.jpg"),
                EXECUTOR);

        clock.advanceDays(6);
        List<Finding> expired = expire.sweep();

        assertThat(expired).hasSize(1);
        assertThat(expired.getFirst().correctiveAction().deadlineBreached()).isTrue();
    }

    @Test
    @DisplayName("a late satisfactory verification closes the action but never erases the breach")
    void aLateClosureKeepsTheBreachRecorded() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        clock.advanceDays(15);
        expire.sweep();
        reportExecution.report(findingId, "thermostat replaced late", List.of("file://photo.jpg"),
                EXECUTOR);

        Finding finding = verify.verify(findingId, true, "measured within range", INSPECTOR);

        assertThat(finding.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.CLOSED);
        assertThat(finding.correctiveAction().deadlineBreached()).isTrue();
        assertThat(finding.correctiveAction().metItsDeadline()).isFalse();
    }

    @Test
    @DisplayName("a failed verification reopens the action for another attempt and keeps every attempt")
    void aFailedVerificationKeepsTheActionOpen() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        reportExecution.report(findingId, "first attempt", List.of("file://a.jpg"), EXECUTOR);
        verify.verify(findingId, false, "still out of range", INSPECTOR);
        reportExecution.report(findingId, "second attempt", List.of("file://b.jpg"), EXECUTOR);

        Finding finding = verify.verify(findingId, true, "now within range", INSPECTOR);

        assertThat(finding.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.CLOSED);
        assertThat(finding.correctiveAction().verifications()).hasSize(2);
        assertThat(finding.correctiveAction().executions()).hasSize(2);
    }

    @Test
    @DisplayName("an action with no reported execution cannot be verified")
    void cannotVerifyWithoutAReportedExecution() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);

        assertThatThrownBy(() -> verify.verify(findingId, true, "looks fine", INSPECTOR))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no reported execution");
    }

    @Test
    @DisplayName("the expiry sweep fires once per action and does not repeat on later runs")
    void theSweepIsNotRepeated() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        clock.advanceDays(15);

        assertThat(expire.sweep()).hasSize(1);
        assertThat(expire.sweep()).isEmpty();
        assertThat(events.ofType(CorrectiveActionExpired.class)).hasSize(1);
    }

    @Test
    @DisplayName("voiding an obligation records no execution and no satisfactory verification")
    void voidingRecordsNoFakeCorrection() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        Finding finding = findings.require(findingId);

        finding.voidObligation(RectificationId.of("rect-1"), "reading was a typo", clock.now());

        assertThat(finding.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.VOIDED);
        assertThat(finding.correctiveAction().executions()).isEmpty();
        assertThat(finding.correctiveAction().verifications()).isEmpty();
        assertThat(finding.correctiveAction().closedAt()).isEmpty();
        assertThat(finding.correctiveAction().blocksCertification()).isFalse();
    }

    @Test
    @DisplayName("a voided obligation keeps a deadline breach that already happened")
    void voidingDoesNotEraseAnExistingBreach() {
        plan.plan(findingId, "replace the thermostat", EXECUTOR, DUE_DATE);
        clock.advanceDays(15);
        expire.sweep();
        Finding finding = findings.require(findingId);

        finding.voidObligation(RectificationId.of("rect-1"), "reading was a typo", clock.now());

        assertThat(finding.correctiveAction().deadlineBreached()).isTrue();
        assertThat(finding.correctiveAction().blocksCertification()).isFalse();
    }

    private FindingId givenAFinding() {
        Finding finding = new Finding(
                FindingId.of("finding-1"),
                InspectionId.of("inspection-1"),
                CriterionId.of("TEMP"),
                AssetId.of("asset-1"),
                PartyId.of("responsible"),
                CriterionResult.REJECTED,
                List.of(new EvaluationReason.RuleVerdict(
                        RuleOutcome.rejected("TEMP_HIGH", Severity.CRITICAL, "too hot"))),
                Severity.CRITICAL,
                List.of(),
                CorrectiveActionId.of("action-1"),
                clock.now());
        findings.save(finding);
        return finding.id();
    }
}
