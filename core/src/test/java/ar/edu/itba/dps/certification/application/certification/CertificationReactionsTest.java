package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.FixedDurationValidityPolicy;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestClock;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificationReactionsTest {
    @Test void duplicateAndStaleEventsEvaluateCurrentFactsRatherThanRestoringOldViolations() {
        var facts = new PolicyFacts(CriterionResult.OBSERVED, Severity.HIGH); facts.plan();
        var c = new CertificateIssuer().issue(CertificateId.of("certificate"), facts.inspection.assetId(), facts.inspection.id(),
                facts.inspection.requireFrozenSchemaVersionId(), CertificateScope.global(),
                FixedDurationValidityPolicy.ofMonths(12).validityFrom(PolicyFacts.AT), null,
                TestPolicies.reference(), CertificateMode.REGULAR);
        var repository = mock(CertificateRepository.class); var inspections = mock(InspectionQuery.class);
        var findings = mock(FindingQuery.class); var audit = mock(AuditRecorder.class); var clock = TestClock.at(PolicyFacts.AT.toString());
        when(repository.findByBackingInspection(facts.inspection.id())).thenReturn(List.of(c));
        when(inspections.require(facts.inspection.id())).thenReturn(facts.inspection);
        when(findings.findingsOf(facts.inspection.id())).thenReturn(List.of(facts.finding));
        var reactions = new CertificationReactions(repository, inspections, new CertificateLifecycle(), audit, findings, clock);
        var event = new CorrectiveActionPlanned(facts.inspection.id(), PolicyFacts.CRITERION, PolicyFacts.AT);
        reactions.handle(event); reactions.handle(event);
        assertThat(c.status()).isEqualTo(CertificateStatus.SUSPENDED);
        verify(repository).save(c);
        verify(audit).recordAutomatic(eq(AuditedElementRef.certificate(c.id().value())), eq(AuditAction.CERTIFICATE_SUSPENDED), any(), anyString());
        facts.finding.voidObligation(RectificationId.of("void"), "removed", PolicyFacts.AT);
        reactions.handle(event); reactions.handle(event);
        assertThat(c.status()).isEqualTo(CertificateStatus.VALID);
        verify(repository, times(2)).save(c);
        verify(audit).recordAutomatic(eq(AuditedElementRef.certificate(c.id().value())), eq(AuditAction.CERTIFICATE_REACTIVATED), any());
    }
}
