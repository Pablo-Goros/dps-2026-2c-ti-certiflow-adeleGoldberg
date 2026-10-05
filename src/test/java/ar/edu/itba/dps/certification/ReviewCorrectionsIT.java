package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.catalogue.*;
import ar.edu.itba.dps.certification.domain.certification.*;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.action.*;
import ar.edu.itba.dps.certification.domain.inspection.*;
import ar.edu.itba.dps.certification.domain.inspection.rectification.*;
import ar.edu.itba.dps.certification.domain.schema.*;
import ar.edu.itba.dps.certification.domain.shared.*;
import ar.edu.itba.dps.certification.domain.shared.answer.*;
import ar.edu.itba.dps.certification.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static ar.edu.itba.dps.certification.support.DomainWorld.*;

class ReviewCorrectionsIT {
    private FullSystem system;
    private Party inspector;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishLaboratorySchema(AssetType.LABORATORY);
        inspector = system.person("Inspector");
        system.actAs(inspector);
        asset = system.asset("Laboratory", AssetType.LABORATORY, system.organization("Owner"));
    }

    @Test
    void directClosureEvaluatesMissingAnswersAndEvidenceInsteadOfAcceptingCallerVerdicts() {
        Inspection inspection = system.inspections.require(start());
        var closure = inspection.close(inspector.id(), system.clock.now());
        assertThat(closure.evaluations().values()).allMatch(e -> e.result() == CriterionResult.REJECTED);
        assertThat(Inspection.class.getMethods()).noneMatch(m -> m.getName().equals("close")
                && java.util.Arrays.stream(m.getParameterTypes()).anyMatch(java.util.Map.class::isAssignableFrom));
    }

    @Test
    void directLoadingRejectsAnAnswerOfTheWrongTypeWithoutChangingTheRecord() {
        Inspection inspection = system.inspections.require(start());
        assertThatThrownBy(() -> inspection.recordAnswer(inspector.id(), TEMPERATURE, YesNoAnswer.yes()))
                .isInstanceOf(DomainException.class).hasMessageContaining("answer refused");
        assertThat(inspection.requireRecord(TEMPERATURE).answer()).isEmpty();
    }

    @Test
    void directRectificationRecomputesTheResultAndPreservesTheOriginal() {
        Inspection inspection = system.inspections.require(close("5"));
        inspection.rectify(RectificationId.of("direct"), inspector.id(), system.clock.now(), "wrong reading",
                List.of(new Correction.AnswerCorrection(TEMPERATURE, Measurement.of("30", "c"))));
        assertThat(inspection.currentEvaluations().get(TEMPERATURE).result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(inspection.requireRecord(TEMPERATURE).evaluations()).hasSize(2);
        assertThat(inspection.requireRecord(TEMPERATURE).evaluations().getFirst().result()).isEqualTo(CriterionResult.APPROVED);
        assertThat(inspection.pendingEvents()).hasSize(1);
    }

    @Test
    void theFactoryBlocksOpenInspectionsAndCertificatesHaveNoPublicConstructor() {
        assertThat(system.certificateFactory.issue(start())).isInstanceOf(IssuanceDecision.Blocked.class);
        assertThat(Certificate.class.getConstructors()).isEmpty();
    }

    @Test
    void anInspectionOlderThanThePreviousCertificateCannotRenewIt() {
        InspectionId older = close("5");
        system.clock.advanceDays(1);
        Certificate previous = issuedCertificate(system.issueCertificate.issue(close("5")));
        system.clock.advanceDays(400);
        assertThatThrownBy(() -> system.renewCertificate.renew(older)).hasMessageContaining("started after");
        assertThat(system.certificates.findLatestForAsset(asset.id(), CertificateScope.global())).contains(previous);
    }

    @Test
    void renewalRepeatedWithTheSameInspectionReturnsTheCertificateAlreadyIssued() {
        issuedCertificate(system.issueCertificate.issue(close("5")));
        system.clock.advanceDays(400);
        InspectionId fresh = close("5");
        Certificate renewed = issuedCertificate(system.renewCertificate.renew(fresh));
        assertThat(system.renewCertificate.renew(fresh))
                .isEqualTo(new IssuanceDecision.AlreadyIssued(renewed.id(), renewed.status()));
    }

    @Test
    void anObservationRevealedAfterIssuanceExpiresEvenWithoutAPlan() {
        InspectionId id = close("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
        rectify(id, "20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        system.clock.advanceDays(30);
        assertThat(system.expireActions.sweep()).isEmpty();
        system.clock.advanceDays(1);
        assertThat(system.expireActions.sweep()).containsExactly(finding);
        assertThat(finding.correctiveAction().deadlineBreached()).isTrue();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(system.expireActions.sweep()).isEmpty();
    }

    @Test
    void aPastDuePlanIsRefusedWithoutRecordingItOrSuspendingACertificate() {
        InspectionId id = close("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
        rectify(id, "20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        assertThatThrownBy(() -> system.planAsResponsible(finding.id(), "fix", PartyId.of("executor"),
                system.clock.today().minusDays(1))).hasMessageContaining("cannot precede planning date");
        assertThat(finding.correctiveAction().plan()).isEmpty();
        assertThat(system.auditTrail.withAction(AuditAction.CORRECTIVE_ACTION_PLANNED)).isEmpty();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    void latePlanningCannotEraseAnUnsweptPlanningDeadlineBreach() {
        InspectionId id = close("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
        rectify(id, "20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        system.clock.advanceDays(31);
        system.planAsResponsible(finding.id(), "fix", PartyId.of("executor"), system.clock.today().plusDays(10));
        assertThat(finding.correctiveAction().deadlineBreached()).isTrue();
        assertThat(finding.correctiveAction().breachedDeadline()).contains(finding.correctiveAction().planningDueDate());
        assertThat(system.expireActions.sweep()).containsExactly(finding);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
    }

    @Test
    void theInspectorCannotBePlannedAsTheirOwnExecutor() {
        Finding finding = system.findings.findByInspection(close("30")).getFirst();
        assertThatThrownBy(() -> system.planAsResponsible(finding.id(), "fix", inspector.id(),
                system.clock.today().plusDays(10))).hasMessageContaining("cannot execute their own correction");
        assertThat(finding.correctiveAction().plan()).isEmpty();
    }

    @Test
    void neitherReportingNorVerificationCanImpersonateAnotherUser() {
        Finding finding = system.findings.findByInspection(close("30")).getFirst();
        PartyId executor = PartyId.of("executor");
        system.planAsResponsible(finding.id(), "fix", executor, system.clock.today().plusDays(10));
        system.actors.actingAs(Actor.user(PartyId.of("intruder"), "intruder"));
        assertThatThrownBy(() -> system.reportExecution.report(finding.id(), "done", List.of("file://proof")))
                .hasMessageContaining("intruder cannot report its execution");
        system.actingAs(executor, () -> system.reportExecution.report(finding.id(), "done", List.of("file://proof")));
        assertThatThrownBy(() -> system.verifyCorrectiveAction.verify(finding.id(), true, "ok"))
                .hasMessageContaining("only the inspector may verify");
        assertThat(finding.correctiveAction().verifications()).isEmpty();
        system.actingAs(inspector.id(), () -> system.verifyCorrectiveAction.verify(finding.id(), true, "ok"));
        var verification = finding.correctiveAction().verifications().getFirst();
        var audit = system.auditTrail.withAction(AuditAction.CORRECTIVE_ACTION_VERIFIED).getFirst();
        assertThat(((Actor.User) audit.actor()).partyId()).isEqualTo(verification.verifiedBy());
    }

    @Test
    void directVerificationEnforcesTheInspectorRoleInTheAggregate() {
        Finding finding = system.findings.findByInspection(close("30")).getFirst();
        PartyId executor = PartyId.of("executor");
        finding.planCorrection(finding.responsible(), new CorrectionPlan("fix", executor, system.clock.today().plusDays(10)), system.clock.today());
        finding.reportCorrectionExecution(new ExecutionReport("done", List.of("file://proof"), executor, system.clock.now()));
        assertThatThrownBy(() -> finding.concludeCorrection(new Verification(true, "ok", executor, system.clock.now()),
                system.clock.today())).hasMessageContaining("only the inspector");
        assertThat(finding.correctiveAction().verifications()).isEmpty();
    }

    @Test
    void returnedDraftsExposeNoPublicMutatorsAndEditingThroughTheRootIsAudited() {
        var schema = system.createSchema.create("Factory", Set.of(AssetType.FACTORY));
        system.openDraft.open(schema.id());
        assertThat(SchemaDraft.class.getDeclaredMethods()).noneMatch(m ->
                Modifier.isPublic(m.getModifiers()) && Set.of("addSection", "removeSection").contains(m.getName()));
        system.editDraft.addSection(schema.id(), Section.of("Safety", 1, temperatureCriterion()));
        system.editDraft.addSection(schema.id(),
                DomainWorld.sectionCovering("Parts", 2, AssetType.FACTORY));
        assertThat(system.publishSchemaVersion.publish(schema.id()).publishedVersion().criteria())
                .hasSize(1 + AssetType.FACTORY.subsystems().size());
        assertThat(system.auditTrail.withAction(AuditAction.SCHEMA_DRAFT_EDITED)).hasSize(3);
    }

    @Test
    void applicabilityRejectsDuplicateOwnersNoOpsAndOrphansAndSupportsTransfer() {
        var source = system.createSchema.create("Source", Set.of(AssetType.FACTORY, AssetType.EQUIPMENT));
        var target = system.createSchema.create("Target", Set.of(AssetType.FACILITY));
        system.openDraft.open(target.id());
        system.editDraft.addSection(target.id(), Section.of("Safety", 1, temperatureCriterion()));
        system.editDraft.addSection(target.id(),
                DomainWorld.sectionCovering("Parts", 2, AssetType.FACILITY));
        assertThat(system.publishSchemaVersion.publish(target.id()).published()).isTrue();
        var change = new ChangeSchemaApplicability(system.schemas, system.schemaApplicability, system.audit);
        assertThatThrownBy(() -> change.applyTo(target.id(), AssetType.FACTORY)).hasMessageContaining("already covered");
        assertThatThrownBy(() -> change.stopApplyingTo(source.id(), AssetType.FACILITY)).hasMessageContaining("does not apply");
        assertThatThrownBy(() -> change.stopApplyingTo(source.id(), AssetType.FACTORY)).hasMessageContaining("without a schema");
        assertThat(system.auditTrail.withAction(AuditAction.SCHEMA_APPLICABILITY_CHANGED)).isEmpty();
        change.transferTo(source.id(), target.id(), AssetType.FACTORY);
        assertThat(system.schemas.findByApplicableAssetType(AssetType.FACTORY)).contains(target);
        assertThat(source.applicableAssetTypes()).containsExactly(AssetType.EQUIPMENT);
        assertThat(system.auditTrail.withAction(AuditAction.SCHEMA_APPLICABILITY_CHANGED)).hasSize(2);
    }

    @Test
    void aSubscriberFailureLeavesTheRectificationPersistedAuditedAndItsEventPending() {
        InspectionId id = close("5");
        issuedCertificate(system.issueCertificate.issue(id));
        var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
        system.events.register(event -> { if (event instanceof CriterionResultRevised && failing.get()) { throw new IllegalStateException("subscriber down"); } });
        assertThatThrownBy(() -> rectify(id, "30")).hasMessage("subscriber down");
        Inspection inspection = system.inspections.require(id);
        assertThat(inspection.rectifications()).hasSize(1);
        assertThat(inspection.currentEvaluations().get(TEMPERATURE).result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(system.findings.findByInspection(id)).hasSize(1);
        assertThat(system.auditTrail.withAction(AuditAction.INSPECTION_RECTIFIED)).hasSize(1);
        assertThat(inspection.pendingEvents()).hasSize(1);
        failing.set(false);
        new ar.edu.itba.dps.certification.application.shared.usecase.PublishPendingDomainEvents(
                system.inspections, system.findings, system.events).publish();
        assertThat(inspection.pendingEvents()).isEmpty();
        assertThat(system.auditTrail.withAction(AuditAction.CERTIFICATE_SUSPENDED)).hasSize(1);
    }

    @Test
    void additionalPolicyRequirementsCannotBypassTheMandatoryIssuanceRules() {
        InspectionId id = close("30");
        var policy = new ar.edu.itba.dps.certification.domain.certification.issuance.CertificateIssuancePolicy(
                List.of(context -> java.util.Optional.empty()));
        var factory = new CertificateFactory(system.inspections, system.findingQuery, system.certificates,
                policy, FixedDurationValidityPolicy.ofMonths(12), system.globalPolicy, system.ids,
                system.clock);
        assertThat(factory.issue(id)).isInstanceOf(IssuanceDecision.Blocked.class);
    }

    @Test
    void retryingAnOldRejectionCannotRestoreASuspensionAlreadyResolvedByANewRectification() {
        InspectionId id = close("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
        var failing = new java.util.concurrent.atomic.AtomicBoolean(true);
        system.events.register(event -> {
            if (event instanceof CriterionResultRevised && failing.get()) {
                throw new IllegalStateException("subscriber down");
            }
        });
        assertThatThrownBy(() -> rectify(id, "30")).hasMessage("subscriber down");
        failing.set(false);
        rectify(id, "5");
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.unresolvedCauses()).isEmpty();
        assertThat(system.auditTrail.withAction(AuditAction.CERTIFICATE_SUSPENDED)).hasSize(1);
        assertThat(system.inspections.require(id).pendingEvents()).isEmpty();
    }

    @Test
    void directClosureWithoutFindingRegistrationCannotCertifyAnObservation() {
        InspectionId id = start();
        system.recordAnswer.record(id, TEMPERATURE, Measurement.of("20", "c"));
        system.recordAnswer.record(id, DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DOCUMENTATION, SAFETY_MANUAL, "file://manual");
        system.inspections.require(id).close(inspector.id(), system.clock.now());
        assertThat(system.certificateFactory.issue(id)).isInstanceOf(IssuanceDecision.Blocked.class);
    }

    @Test
    void auditRejectsBlankReasonsAndDistinguishesInvalidArgumentsFromBusinessFailures() {
        assertThatThrownBy(() -> new ar.edu.itba.dps.certification.domain.audit.AuditEntry(
                ar.edu.itba.dps.certification.domain.audit.AuditedElementRef.inspection("id"),
                AuditAction.INSPECTION_RECTIFIED, system.clock.now(), Actor.system(), java.util.Optional.of("  "),
                ar.edu.itba.dps.certification.domain.audit.AuditDetail.created("change")))
                .isInstanceOf(InvalidArgumentException.class).hasMessageContaining("audit reason");
        assertThatThrownBy(() -> Validate.required(null, "argument")).isInstanceOf(InvalidArgumentException.class);
        assertThatThrownBy(() -> Validate.ensure(false, "business rule")).isExactlyInstanceOf(DomainException.class);
    }

    @Test
    void assetCharacteristicsAreCheckedAgainstThePredefinedNames() {
        assertThatThrownBy(() -> new Asset(AssetId.of("bad"), "Bad", AssetType.EQUIPMENT,
                java.util.Map.of("invented", "value"), asset.responsible(), "here"))
                .hasMessageContaining("must be predefined");
    }

    private InspectionId start() {
        var id = system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id();
        system.startInspection.start(id);
        return id;
    }
    private InspectionId close(String reading) {
        var id = start();
        system.recordAnswer.record(id, TEMPERATURE, Measurement.of(reading, "c"));
        system.recordAnswer.record(id, DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DOCUMENTATION, SAFETY_MANUAL, "file://manual");
        system.closeInspection.close(id);
        return id;
    }
    private Rectification rectify(InspectionId id, String reading) {
        return system.actingAs(inspector.id(), () -> system.rectifyClosedInspection.rectify(id, "wrong reading",
                List.of(new Correction.AnswerCorrection(TEMPERATURE, Measurement.of(reading, "c")))));
    }
}
