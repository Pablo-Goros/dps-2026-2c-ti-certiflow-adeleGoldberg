package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.issuance.*;
import ar.edu.itba.dps.certification.domain.certification.policy.JurisdictionCertificationPolicy;
import ar.edu.itba.dps.certification.domain.inspection.*;
import ar.edu.itba.dps.certification.domain.inspection.rectification.*;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.schema.rule.*;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.support.*;

import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CertificateIssuerTest {
    private final CertificateIssuer issuer = new CertificateIssuer();
    private final CertificateId id = CertificateId.of("certificate");
    private final PolicyFacts facts = new PolicyFacts(CriterionResult.APPROVED, null);
    private final JurisdictionCertificationPolicy policy =
            TestPolicies.profile("REFERENCE", 1, Set.of(Severity.CRITICAL), true, 12, 6);

    @Test void regularIssuanceDerivesProvenanceAndTwelveMonthValidityFromDecision() {
        var assessment = policy.assess(facts.context(), PolicyFacts.AT);
        var certificate = issuer.issue(id, facts.inspection, assessment, null);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.assetId()).isEqualTo(facts.inspection.assetId());
        assertThat(certificate.backingInspectionId()).isEqualTo(assessment.inspectionId());
        assertThat(certificate.schemaVersionId()).isEqualTo(facts.inspection.requireFrozenSchemaVersionId());
        assertThat(certificate.scope()).isEqualTo(assessment.scope());
        assertThat(certificate.policy()).isEqualTo(assessment.policy());
        assertThat(certificate.mode()).isEqualTo(CertificateMode.REGULAR);
        assertThat(certificate.validity().issuedAt()).isEqualTo(PolicyFacts.AT);
        assertThat(certificate.validity().expiresAt()).isEqualTo(Instant.parse("2027-03-01T10:00:00Z"));
        assertThat(CertificationAssessment.class.getConstructors()).isEmpty();
    }

    @Test void conditionalDecisionUsesItsOwnShorterDuration() {
        var pending = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW); pending.plan();
        var certificate = issuer.issue(id, pending.inspection, policy.assess(pending.context(), PolicyFacts.AT), null);
        assertThat(certificate.mode()).isEqualTo(CertificateMode.CONDITIONAL);
        assertThat(certificate.validity().expiresAt()).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"));
    }

    @Test void missingFindingsCannotHideRejectedUnansweredCriteria() {
        var pending = new PolicyFacts(CriterionResult.REJECTED, Severity.CRITICAL);
        var assessment = policy.assess(pending.context(PolicyFacts.today(), List.of()), PolicyFacts.AT);
        assertThat(assessment.blockers()).anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance)
                .anyMatch(IssuanceBlocker.BlockingSeverity.class::isInstance);
        assertThatThrownBy(() -> issuer.issue(id, pending.inspection, assessment, null)).hasMessageContaining("blockers");
        assertThatThrownBy(() -> new Certificate(id, pending.inspection, assessment, null)).hasMessageContaining("blockers");
    }

    @Test void openInspectionWithNoAnswersCannotBeCertified() {
        var open = new Inspection(facts.inspection.id(), facts.inspection.assetId(), PolicyFacts.INSPECTOR, PolicyFacts.today());
        var version = new SchemaVersion(facts.inspection.requireFrozenSchemaVersionId(),
                List.of(Section.of("section", 1, new Criterion(PolicyFacts.CRITERION,
                        new MappedOptionsRule(Map.of("yes", RuleOutcome.approved("OK", "ok"))), List.of()))), PolicyFacts.AT);
        open.start(PolicyFacts.INSPECTOR, version, facts.inspection.assetSnapshot().orElseThrow(), PolicyFacts.AT);
        var context = new CertificationContext(open, List.of(), PolicyFacts.today(), CertificateScope.global(),
                Optional.empty(), Optional.empty());
        var assessment = policy.assess(context, PolicyFacts.AT);
        assertThat(assessment.blockers()).contains(new IssuanceBlocker.InspectionNotClosed());
        assertThatThrownBy(() -> issuer.issue(id, open, assessment, null)).hasMessageContaining("blockers");
        assertThatThrownBy(() -> issuer.issue(id, open, policy.assess(facts.context(), PolicyFacts.AT), null))
                .hasMessageContaining("changed after assessment");
    }

    @Test void assessmentCannotBeTransferredToAnotherInspectionOrDifferentStateWithTheSameId() {
        var other = CertificationScenarios.approvedInspection(InspectionId.of("other"), facts.inspection.assetId(),
                facts.inspection.requireFrozenSchemaVersionId(), Set.of(), PolicyFacts.AT);
        var assessment = policy.assess(facts.context(), PolicyFacts.AT);
        assertThatThrownBy(() -> issuer.issue(id, other, assessment, null)).hasMessageContaining("belong");
        var rejected = new PolicyFacts(CriterionResult.REJECTED, Severity.CRITICAL);
        assertThatThrownBy(() -> issuer.issue(id, rejected.inspection, assessment, null))
                .hasMessageContaining("changed after assessment");
    }

    @Test void issuanceCannotPrecedeClosure() {
        assertThatThrownBy(() -> policy.assess(facts.context(), PolicyFacts.AT.minusSeconds(1)))
                .hasMessageContaining("precede closure");
    }

    @Test void rectificationInvalidatesAnEarlierFavorableAssessment() {
        var assessment = policy.assess(facts.context(), PolicyFacts.AT);
        facts.inspection.rectify(RectificationId.of("rect"), PolicyFacts.INSPECTOR, PolicyFacts.AT,
                "correct the answer", List.of(new Correction.AnswerCorrection(PolicyFacts.CRITERION, OptionAnswer.of("answer"))));
        assertThatThrownBy(() -> issuer.issue(id, facts.inspection, assessment, null))
                .hasMessageContaining("changed after assessment");
    }

    @Test void changesToFindingsInvalidateTheDecisionEvenWhenTheInspectionDoesNotChange() {
        var pending = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW); pending.plan();
        var assessment = policy.assess(pending.context(), PolicyFacts.AT);
        pending.verify();
        assertThatThrownBy(() -> issuer.issue(id, pending.inspection, assessment, null))
                .hasMessageContaining("facts changed after assessment");
        assertThat(issuer.issue(id, pending.inspection, policy.assess(pending.context(), PolicyFacts.AT), null).mode())
                .isEqualTo(CertificateMode.REGULAR);
    }

    @Test void strategiesCanOnlyAddBlockersAndCannotRemoveCommonRequirements() {
        var pending = new PolicyFacts(CriterionResult.OBSERVED, Severity.LOW);
        var assessment = policy.assess(pending.context(), PolicyFacts.AT).withAdditionalBlockers(List.of());
        assertThat(assessment.mode()).isEmpty();
        assertThat(assessment.blockers()).anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance);
        assertThatThrownBy(() -> issuer.issue(id, pending.inspection, assessment, null)).hasMessageContaining("blockers");
    }

    @Test void anUndeclaredSubsystemCannotBeCertified() {
        var context = new CertificationContext(facts.inspection, List.of(), PolicyFacts.today(),
                CertificateScope.of(Subsystem.of("absent")),
                Optional.empty(), Optional.empty());
        assertThatThrownBy(() -> issuer.issue(id, facts.inspection, policy.assess(context, PolicyFacts.AT), null))
                .hasMessageContaining("scope must be certified");
    }

    @Test void policyMustBelongToTheInspectionJurisdiction() {
        var foreign = TestPolicies.profile("OTHER", 1, Set.of(), true, 12, 6);
        assertThatThrownBy(() -> issuer.issue(id, facts.inspection, foreign.assess(facts.context(), PolicyFacts.AT), null))
                .hasMessageContaining("jurisdiction");
    }

    @Test void renewalNeedsAnExpiredPredecessorAndANewerInspection() {
        var previous = issuer.issue(id, facts.inspection, policy.assess(facts.context(), PolicyFacts.AT), null);
        var next = CertificationScenarios.approvedInspection(InspectionId.of("next"), previous.assetId(),
                previous.schemaVersionId(), Set.of(), PolicyFacts.AT.plusSeconds(1));
        assertThatThrownBy(() -> CertificationScenarios.issue(CertificateId.of("next"), next, CertificateScope.global(),
                policy.snapshot(), PolicyFacts.AT.plusSeconds(1), previous)).hasMessageContaining("must be expired");
        var expiredAt = previous.validity().expiresAt();
        assertThatThrownBy(() -> issuer.issue(CertificateId.of("next"), facts.inspection,
                policy.assess(facts.context(expiredAt.atZone(ZoneOffset.UTC).toLocalDate(), List.of()), expiredAt), previous))
                .hasMessageContaining("new inspection");
        var renewed = CertificationScenarios.issue(CertificateId.of("next"), next, CertificateScope.global(),
                policy.snapshot(), expiredAt, previous);
        assertThat(renewed.previousCertificateId()).contains(previous.id());
    }

    @Test void renewalCannotUseAnOlderInspectionEvenIfItHasADifferentId() {
        var previous = issuer.issue(id, facts.inspection, policy.assess(facts.context(), PolicyFacts.AT), null);
        var older = CertificationScenarios.approvedInspection(InspectionId.of("older"), previous.assetId(),
                previous.schemaVersionId(), Set.of(), PolicyFacts.AT.minusSeconds(1));
        assertThatThrownBy(() -> CertificationScenarios.issue(CertificateId.of("next"), older, CertificateScope.global(),
                policy.snapshot(), previous.validity().expiresAt(), previous)).hasMessageContaining("started after");
    }

    @Test void renewalCannotChangeTheAssetOrScopeOfItsPredecessor() {
        var previous = issuer.issue(id, facts.inspection, policy.assess(facts.context(), PolicyFacts.AT), null);
        var at = previous.validity().expiresAt();
        var otherAsset = CertificationScenarios.approvedInspection(InspectionId.of("next"),
                AssetId.of("other-asset"),
                previous.schemaVersionId(), Set.of(), at);
        assertThatThrownBy(() -> CertificationScenarios.issue(CertificateId.of("next"), otherAsset,
                CertificateScope.global(), policy.snapshot(), at, previous)).hasMessageContaining("same asset and scope");
        var part = DomainWorld.ELECTRICAL;
        var partial = CertificationScenarios.approvedInspection(InspectionId.of("partial"), previous.assetId(),
                previous.schemaVersionId(), Set.of(part), at);
        assertThatThrownBy(() -> CertificationScenarios.issue(CertificateId.of("next"), partial,
                CertificateScope.of(part), policy.snapshot(), at, previous)).hasMessageContaining("same asset and scope");
    }
}
