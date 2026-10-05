package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.certification.port.CertificationPolicyRegistry;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.policy.JurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.support.PolicyFacts;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.*;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateFactoryTest {
    private final InspectionQuery inspections = mock(InspectionQuery.class);
    private final FindingQuery findings = mock(FindingQuery.class);
    private final CertificateRepository certificates = mock(CertificateRepository.class);
    private final AssetDirectory assets = mock(AssetDirectory.class);
    private final CertificationPolicyRegistry policies = mock(CertificationPolicyRegistry.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final Clock clock = mock(Clock.class);
    private final PolicyFacts facts = new PolicyFacts(CriterionResult.APPROVED, null);
    private final JurisdictionCertificationPolicy policy = TestPolicies.profile("REFERENCE", 1, Set.of(), true, 12, 6);
    private final CertificateFactory factory = new CertificateFactory(inspections, findings, certificates, assets,
            policies, new AllSubsystemsMustBeInForce(), ids, clock);
    @BeforeEach void setup() {
        when(inspections.require(facts.inspection.id())).thenReturn(facts.inspection);
        when(assets.jurisdictionOf(facts.inspection.assetId())).thenReturn(TestPolicies.REFERENCE);
        when(policies.resolve(TestPolicies.REFERENCE)).thenReturn(policy);
        when(clock.now()).thenReturn(PolicyFacts.AT);
        when(ids.newIdentifier()).thenReturn("certificate");
    }
    @Test void eligibilityAndIssuanceSharePolicyTimeAndMode() {
        var assessment = factory.assess(facts.inspection.id());
        var certificate = ((IssuanceDecision.Issued) factory.issue(facts.inspection.id())).certificate();
        assertThat(certificate.policy()).isEqualTo(assessment.policy());
        assertThat(certificate.mode()).isEqualTo(assessment.mode().orElseThrow());
        assertThat(certificate.validity().issuedAt()).isEqualTo(assessment.evaluatedAt());
        verify(assets, times(2)).jurisdictionOf(facts.inspection.assetId());
        verify(policies, times(2)).resolve(TestPolicies.REFERENCE);
        verify(clock, times(2)).now(); verify(clock, never()).today();
    }
    @Test void resolutionFailuresNeverConsumeCertificateIdentifiers() {
        when(policies.resolve(TestPolicies.REFERENCE)).thenThrow(new PolicyResolutionException("missing"));
        assertThatThrownBy(() -> factory.issue(facts.inspection.id())).isInstanceOf(PolicyResolutionException.class);
        verifyNoInteractions(ids); verify(certificates, never()).save(any());
    }
    @Test void anAssignedInspectionStillResolvesItsPolicyAndReportsClosureBlocker() {
        var assigned = new Inspection(facts.inspection.id(), facts.inspection.assetId(),
                PolicyFacts.INSPECTOR, PolicyFacts.today());
        when(inspections.require(assigned.id())).thenReturn(assigned);
        var blocked = (IssuanceDecision.Blocked) factory.issue(assigned.id());
        assertThat(blocked.assessment().policy()).isEqualTo(policy.snapshot());
        assertThat(blocked.blockers()).contains(new IssuanceBlocker.InspectionNotClosed());
        verifyNoInteractions(ids);
    }
    @Test void anInconsistentPolicyCannotEmit() {
        when(policies.resolve(TestPolicies.REFERENCE)).thenReturn(TestPolicies.profile("OTHER", 1, Set.of(), true, 12, 6));
        assertThatThrownBy(() -> factory.issue(facts.inspection.id())).isInstanceOf(PolicyResolutionException.class);
        verifyNoInteractions(ids);
    }
    @Test void invalidValidityAndFutureClosureFailBeforeGeneratingId() {
        var strategy = mock(JurisdictionCertificationPolicy.class);
        when(strategy.snapshot()).thenReturn(policy.snapshot()); when(strategy.reference()).thenReturn(policy.reference());
        when(strategy.assess(any(), any())).thenAnswer(call -> policy.assess(call.getArgument(0), call.getArgument(1)));
        when(strategy.validityFrom(any(), any())).thenReturn(new ValidityPeriod(PolicyFacts.AT.minusSeconds(1), PolicyFacts.AT.plusSeconds(1)));
        when(policies.resolve(TestPolicies.REFERENCE)).thenReturn(strategy);
        assertThatThrownBy(() -> factory.issue(facts.inspection.id())).hasMessageContaining("begin at issuance");
        when(clock.now()).thenReturn(PolicyFacts.AT.minusSeconds(1));
        assertThatThrownBy(() -> factory.issue(facts.inspection.id())).hasMessageContaining("precede closure");
        verifyNoInteractions(ids);
    }
    @Test void repetitionUsesStoredDefinitionWithoutConsultingCurrentConfiguration() {
        var stored = new CertificateIssuer().issue(CertificateId.of("old"), facts.inspection.assetId(), facts.inspection.id(),
                facts.inspection.requireFrozenSchemaVersionId(), CertificateScope.global(), policy.validityFrom(PolicyFacts.AT, CertificateMode.REGULAR),
                null, policy.snapshot(), CertificateMode.REGULAR);
        when(certificates.findByBackingInspection(facts.inspection.id(), CertificateScope.global())).thenReturn(Optional.of(stored));
        var repeated = (IssuanceDecision.AlreadyIssued) factory.issue(facts.inspection.id());
        assertThat(repeated.policy()).isEqualTo(stored.policy()); assertThat(repeated.mode()).isEqualTo(stored.mode());
        verifyNoInteractions(assets, policies, ids, clock);
    }
    @Test void repeatedRenewalDoesNotNeedAnActivePolicyOrAnUnexpiredPredecessor() {
        var stored = new CertificateIssuer().issue(CertificateId.of("renewed"), facts.inspection.assetId(), facts.inspection.id(),
                facts.inspection.requireFrozenSchemaVersionId(), CertificateScope.global(), policy.validityFrom(PolicyFacts.AT, CertificateMode.REGULAR),
                CertificateId.of("predecessor"), policy.snapshot(), CertificateMode.REGULAR);
        when(certificates.findByBackingInspection(facts.inspection.id(), CertificateScope.global())).thenReturn(Optional.of(stored));
        var repeated = (IssuanceDecision.AlreadyIssued) factory.renew(facts.inspection.id());
        assertThat(repeated.policy()).isEqualTo(stored.policy());
        assertThat(repeated.certificateId()).isEqualTo(stored.id());
        verifyNoInteractions(assets, policies, ids, clock);
    }
    @Test void aCustomStrategyCannotDisableCommonGuarantees() {
        var pending = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW);
        when(inspections.require(pending.inspection.id())).thenReturn(pending.inspection);
        when(findings.findingsOf(pending.inspection.id())).thenReturn(List.of(pending.finding));
        var strategy = mock(JurisdictionCertificationPolicy.class);
        when(strategy.snapshot()).thenReturn(policy.snapshot()); when(strategy.reference()).thenReturn(policy.reference());
        when(strategy.assess(any(), any())).thenReturn(new CertificationAssessment(pending.inspection.id(), CertificateScope.global(),
                PolicyFacts.AT, policy.snapshot(), List.of(), Optional.of(CertificateMode.CONDITIONAL)));
        when(policies.resolve(TestPolicies.REFERENCE)).thenReturn(strategy);
        assertThat(((IssuanceDecision.Blocked) factory.issue(pending.inspection.id())).blockers()).anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance);
        verifyNoInteractions(ids);
    }
}
