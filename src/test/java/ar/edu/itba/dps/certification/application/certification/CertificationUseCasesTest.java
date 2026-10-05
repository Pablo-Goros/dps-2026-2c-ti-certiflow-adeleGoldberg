package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.FixedDurationValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.policy.ConfiguredJurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificationUseCasesTest {
    private final CertificateFactory factory = mock(CertificateFactory.class);
    private final CertificateRepository repository = mock(CertificateRepository.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final PolicyFacts facts = new PolicyFacts(CriterionResult.APPROVED, null);
    private Certificate certificate() {
        return new CertificateIssuer().issue(CertificateId.of("certificate"), facts.inspection.assetId(), facts.inspection.id(),
                facts.inspection.requireFrozenSchemaVersionId(), CertificateScope.global(),
                new FixedDurationValidityPolicy(TestPolicies.reference().regularDuration()).validityFrom(PolicyFacts.AT),
                CertificateId.of("previous"), TestPolicies.reference(), CertificateMode.REGULAR);
    }
    @Test void successIsSavedBeforeStructuredAuditAndSaveFailureDoesNotAuditSuccess() {
        var c = certificate(); var decision = new IssuanceDecision.Issued(c);
        when(factory.issue(facts.inspection.id())).thenReturn(decision);
        new IssueCertificate(factory, repository, audit).issue(facts.inspection.id());
        var order = inOrder(repository, audit);
        order.verify(repository).save(c);
        order.verify(audit).record(AuditedElementRef.certificate(c.id().value()), AuditAction.CERTIFICATE_ISSUED, AuditDetail.certification("issue", decision));
        reset(audit, repository); doThrow(new IllegalStateException("save failed")).when(repository).save(c);
        assertThatThrownBy(() -> new IssueCertificate(factory, repository, audit).issue(facts.inspection.id())).hasMessage("save failed");
        verifyNoInteractions(audit);
    }
    @Test void blockedRenewalAuditsPolicyAndAllReasonsWithoutSaving() {
        var pending = new PolicyFacts(CriterionResult.REJECTED, Severity.CRITICAL);
        var policy = new ConfiguredJurisdictionCertificationPolicy(TestPolicies.reference());
        var decision = new IssuanceDecision.Blocked(policy.assess(pending.context(), PolicyFacts.AT));
        when(factory.renew(pending.inspection.id())).thenReturn(decision);
        new RenewCertificate(factory, repository, audit).renew(pending.inspection.id());
        verifyNoInteractions(repository);
        verify(audit).record(AuditedElementRef.inspection(pending.inspection.id().value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                AuditDetail.certification("renew", decision));
    }
    @Test void repetitionsNeverSaveOrAuditANewEmissionAndResolutionErrorsAreExplicit() {
        var c = certificate();
        when(factory.issue(facts.inspection.id())).thenReturn(new IssuanceDecision.AlreadyIssued(c.id(), c.status(), c.policy(), c.mode()));
        new IssueCertificate(factory, repository, audit).issue(facts.inspection.id()); verifyNoInteractions(repository, audit);
        when(factory.issue(facts.inspection.id())).thenThrow(new PolicyResolutionException("missing"));
        assertThatThrownBy(() -> new IssueCertificate(factory, repository, audit).issue(facts.inspection.id())).isInstanceOf(PolicyResolutionException.class);
        verify(audit).record(AuditedElementRef.inspection(facts.inspection.id().value()), AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                new AuditDetail.PolicyResolutionFailed("issue", facts.inspection.id(), CertificateScope.global(), "missing"));
        verifyNoInteractions(repository);
    }
}
