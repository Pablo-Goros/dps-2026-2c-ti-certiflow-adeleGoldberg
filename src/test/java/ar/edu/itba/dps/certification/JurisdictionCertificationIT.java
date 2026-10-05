package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.policy.PolicyResolutionException;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.time.Duration;
import java.util.*;

import org.junit.jupiter.api.*;

import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.*;

class JurisdictionCertificationIT {
    private FullSystem system;
    private Party owner, inspector;
    @BeforeEach void setup() {
        system = new FullSystem(); owner = system.person("Owner"); inspector = system.person("Inspector"); system.actAs(inspector);
        var schema = system.createSchema.create("Lab", Set.of(AssetType.LABORATORY));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(Severity.MEDIUM), DomainWorld.documentationCriterion()));
        system.publishSchemaVersion.publish(schema.id());
    }
    private Asset asset(String jurisdiction) {
        return system.registerAsset.register("Lab", AssetType.LABORATORY, owner.id(), "Here", Map.of(), JurisdictionId.of(jurisdiction));
    }
    private InspectionId inspect(Asset asset, String reading) {
        var id = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.TEMPERATURE, Measurement.of(reading, "c"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "manual");
        system.closeInspection.close(id); return id;
    }
    private void plan(InspectionId id) {
        for (var f : system.findings.findByInspection(id)) {
            if (f.pendingNonConformity() && f.correctiveAction().awaitingPlan()) {
                system.planAsResponsible(f.id(), "repair", owner.id(), system.clock.today().plusDays(20));
            }
        }
    }
    private void rectify(InspectionId id, String reading) {
        system.rectifyClosedInspection.rectify(id, "reading corrected",
                List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE, Measurement.of(reading, "c"))));
    }
    @Test void equivalentResultsChooseDifferentDecisionsAndDurationsWithStructuredAudit() {
        var a = inspect(asset("A"), "20"); var b = inspect(asset("B"), "20"); plan(a); plan(b);
        var assessment = system.evaluateEligibility.assess(a);
        var issued = issuedCertificate(system.issueCertificate.issue(a));
        var blocked = (IssuanceDecision.Blocked) system.issueCertificate.issue(b);
        assertThat(issued.mode()).isEqualTo(CertificateMode.CONDITIONAL);
        assertThat(issued.policy()).isEqualTo(assessment.policy());
        assertThat(issued.validity().expiresAt()).isEqualTo(java.time.Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(blocked.blockers()).anyMatch(IssuanceBlocker.BlockingSeverity.class::isInstance).anyMatch(IssuanceBlocker.ConditionalNotAllowed.class::isInstance);
        assertThat(system.auditTrail.withAction(AuditAction.CERTIFICATE_ISSUED)).singleElement().satisfies(entry ->
                assertThat(entry.detail()).isInstanceOfSatisfying(AuditDetail.CertificationDecision.class, detail -> {
                    assertThat(detail.policy()).isEqualTo(issued.policy()); assertThat(detail.mode()).contains(issued.mode());
                }));
        var report = system.generateCertificateReport.reportBlockedAttempt(blocked);
        assertThat(report.policy()).isEqualTo(blocked.assessment().policy()); assertThat(report.scope()).isEqualTo(blocked.assessment().scope());
        var approvedA = issuedCertificate(system.issueCertificate.issue(inspect(asset("A"), "5")));
        var approvedB = issuedCertificate(system.issueCertificate.issue(inspect(asset("B"), "5")));
        assertThat(approvedA.mode()).isEqualTo(CertificateMode.REGULAR);
        assertThat(approvedA.validity().expiresAt()).isAfter(approvedB.validity().expiresAt());
    }
    @Test void thirdJurisdictionIsRegisteredWithoutRebuildingFactoryAndUnknownHasNoFallback() {
        var id = inspect(asset("C"), "1"); plan(id);
        assertThatThrownBy(() -> system.issueCertificate.issue(id)).isInstanceOf(PolicyResolutionException.class);
        assertThat(system.certificates.findAll()).isEmpty();
        assertThat(system.auditTrail.withAction(AuditAction.CERTIFICATE_ISSUANCE_BLOCKED)).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).isInstanceOf(AuditDetail.PolicyResolutionFailed.class));
        system.policies.register(JurisdictionId.of("C"), TestPolicies.profile("C", 1, Set.of(), true, 9, 3));
        assertThat(issuedCertificate(system.issueCertificate.issue(id)).mode()).isEqualTo(CertificateMode.CONDITIONAL);
    }
    @Test void historyAndRepetitionSurvivePolicyChangesWhileRenewalUsesCurrentRevision() {
        var asset = asset("A"); var id = inspect(asset, "5");
        var original = issuedCertificate(system.issueCertificate.issue(id));
        system.policies.register(JurisdictionId.of("A"), TestPolicies.profile("A", 2, Set.of(Severity.CRITICAL), true, 24, 3));
        var repeated = (IssuanceDecision.AlreadyIssued) system.issueCertificate.issue(id);
        assertThat(repeated.policy()).isEqualTo(original.policy());
        assertThat(system.generateCertificateReport.generate(original.id()).policy()).isEqualTo(original.policy());
        system.clock.advance(Duration.between(system.clock.now(), original.validity().expiresAt()));
        var nextId = inspect(asset, "5");
        var renewed = issuedCertificate(system.renewCertificate.renew(nextId));
        assertThat(renewed.previousCertificateId()).contains(original.id());
        assertThat(renewed.policy().reference().revision()).isEqualTo(2);
        system.policies.register(JurisdictionId.of("A"), TestPolicies.profile("A", 3, Set.of(), true, 6, 6));
        assertThat(((IssuanceDecision.AlreadyIssued) system.renewCertificate.renew(nextId)).policy()).isEqualTo(renewed.policy());
        assertThat(system.auditTrail.withAction(AuditAction.CERTIFICATE_ISSUED)).hasSize(2);
    }
    @Test void lifecycleUsesHistoricalPolicyAllowsPlannedRejectionsAndRejectsNewBlockingSeverity() {
        system.policies.register(JurisdictionId.of("C"), TestPolicies.profile("C", 1, Set.of(Severity.CRITICAL), true, 12, 6));
        var id = inspect(asset("C"), "20"); plan(id);
        var certificate = issuedCertificate(system.issueCertificate.issue(id));
        system.policies.register(JurisdictionId.of("C"), TestPolicies.profile("C", 2, Set.of(Severity.HIGH, Severity.CRITICAL), false, 6, 6));
        rectify(id, "1"); // HIGH rejection is permitted by the historical revision.
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        rectify(id, "30"); // CRITICAL remains blocking.
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        rectify(id, "1");
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.mode()).isEqualTo(CertificateMode.CONDITIONAL);
        rectify(id, "5"); rectify(id, "20"); // New action has no plan yet.
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        plan(id);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        system.clock.advanceDays(21); system.expireActions.sweep();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
    }
    @Test void regularCannotBecomeConditionalImplicitlyAndVerificationResolvesCurrentObservation() {
        var id = inspect(asset("A"), "5"); var certificate = issuedCertificate(system.issueCertificate.issue(id));
        rectify(id, "20"); plan(id);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        var finding = system.findings.findByInspection(id).getFirst();
        system.actingAs(owner.id(), () -> system.reportExecution.report(finding.id(), "done", List.of("proof")));
        system.actingAs(inspector.id(), () -> system.verifyCorrectiveAction.verify(finding.id(), true, "verified"));
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.mode()).isEqualTo(CertificateMode.REGULAR);
    }
    @Test void partialPoliciesAndModesArePreservedInConditionalGlobal() {
        system.publishSubsystemSchema(AssetType.FACILITY);
        var asset = system.registerAsset.register("Facility", AssetType.FACILITY, owner.id(), "Here", Map.of(),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("A"));
        var id = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id(); system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("untidy"));
        system.recordAnswer.record(id, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "manual"); system.closeInspection.close(id); plan(id);
        var electrical = issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.ELECTRICAL));
        system.policies.register(JurisdictionId.of("A"), TestPolicies.profile("A", 2, Set.of(Severity.HIGH), true, 24, 3));
        var pressure = issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.PRESSURE));
        var global = system.deriveGlobalCertificate.deriveFor(id).derivedCertificate().orElseThrow();
        assertThat(global.mode()).isEqualTo(CertificateMode.CONDITIONAL);
        assertThat(global.validity().expiresAt()).isEqualTo(electrical.validity().expiresAt());
        assertThat(global.provenance()).extracting(p -> p.policy().reference().revision()).containsExactly(1, 2);
        assertThat(pressure.mode()).isEqualTo(CertificateMode.REGULAR);
        system.rectifyClosedInspection.rectify(id, "dangerous wiring", List.of(new Correction.AnswerCorrection(DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("hazardous"))));
        assertThat(electrical.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(pressure.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(system.deriveGlobalCertificate.deriveFor(id).derivedCertificate()).isEmpty();
    }
}
