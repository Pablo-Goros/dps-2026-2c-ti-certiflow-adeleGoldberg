package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.inspection.usecase.ReassignInspection;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.report.ReportedValue;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static ar.edu.itba.dps.certification.support.DomainWorld.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Regression tests for the second review, done before starting Entrega 2. Every test reproduces
 * a gap that the previous version accepted: a validation that could be skipped, a rule of the
 * PRD that was not enforced, or an audit entry that misdescribed what happened.
 */
class SecondReviewIT {
    private static final PartyId INTRUDER = PartyId.of("intruder");

    private FullSystem system;
    private Party inspector;
    private Party owner;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishLaboratorySchema(AssetType.LABORATORY);
        inspector = system.person("Inspector");
        owner = system.organization("Owner");
        asset = system.asset("Laboratory", AssetType.LABORATORY, owner);
        system.actAs(inspector);
    }

    // --- Who acts on an inspection ------------------------------------------------------------

    @Test
    @DisplayName("only the assigned inspector starts, loads and closes the inspection")
    void onlyTheAssignedInspectorOperatesTheInspection() {
        InspectionId id = assign();
        assertThatThrownBy(() -> asIntruder(() -> system.startInspection.start(id)))
                .hasMessageContaining("only the assigned inspector");
        system.startInspection.start(id);

        assertThatThrownBy(() -> asIntruder(() -> system.recordAnswer.record(id, TEMPERATURE, Measurement.of("5", "c"))))
                .hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> asIntruder(() -> system.attachEvidence.attach(id, DOCUMENTATION, SAFETY_MANUAL, "file://x")))
                .hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> asIntruder(() -> system.recordNote.record(id, Optional.empty(), "looks fine")))
                .hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> asIntruder(() -> system.closeInspection.close(id)))
                .hasMessageContaining("only the assigned inspector");

        Inspection inspection = system.inspections.require(id);
        assertThat(inspection.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(inspection.requireRecord(TEMPERATURE).answer()).isEmpty();
        assertThat(inspection.requireRecord(DOCUMENTATION).evidence()).isEmpty();
        assertThat(inspection.notes()).isEmpty();
        assertThat(system.auditTrail.withAction(AuditAction.INSPECTION_STARTED)).singleElement()
                .satisfies(entry -> assertThat(((Actor.User) entry.actor()).partyId()).isEqualTo(inspector.id()));
    }

    @Test
    @DisplayName("the aggregate itself refuses another party, not only the use case")
    void theAggregateRefusesAnotherParty() {
        Inspection inspection = system.inspections.require(start());
        assertThatThrownBy(() -> inspection.recordAnswer(INTRUDER, TEMPERATURE, Measurement.of("5", "c")))
                .isInstanceOf(DomainException.class).hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> inspection.close(INTRUDER, system.clock.now()))
                .isInstanceOf(DomainException.class).hasMessageContaining("only the assigned inspector");
        assertThat(inspection.status()).isEqualTo(InspectionStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("notes are authored by the inspector and evidence is resolved against the frozen version")
    void notesAndEvidenceAreBuiltByTheAggregate() {
        InspectionId id = start();
        var note = system.recordNote.record(id, Optional.of(TEMPERATURE), "probe near the door");
        assertThat(note.author()).isEqualTo(inspector.id());

        Inspection inspection = system.inspections.require(id);
        assertThatThrownBy(() -> inspection.attachEvidence(inspector.id(), DOCUMENTATION, "invented label",
                "ev-1", "file://x", system.clock.now())).hasMessageContaining("declares no evidence requirement");
        var evidence = inspection.attachEvidence(inspector.id(), DOCUMENTATION, SAFETY_MANUAL, "ev-2",
                "file://manual", system.clock.now());
        assertThat(evidence.type()).isEqualTo(EvidenceType.DOCUMENT);
    }

    @Test
    @DisplayName("an inspection is assigned and reassigned only to a registered person")
    void inspectorsMustBeRegisteredPeople() {
        assertThatThrownBy(() -> system.assignInspection.assign(asset.id(), PartyId.of("ghost"), system.clock.today()))
                .hasMessageContaining("is not registered");
        assertThatThrownBy(() -> system.assignInspection.assign(asset.id(), owner.id(), system.clock.today()))
                .hasMessageContaining("cannot be assigned as inspector");
        assertThat(system.inspections.findAll()).isEmpty();

        InspectionId id = assign();
        var reassign = new ReassignInspection(system.inspections, system.catalogue.parties, system.audit);
        assertThatThrownBy(() -> reassign.reassign(id, owner.id(), system.clock.today()))
                .hasMessageContaining("cannot be assigned as inspector");
        assertThat(system.inspections.require(id).inspector()).isEqualTo(inspector.id());
    }

    // --- Who plans a correction ---------------------------------------------------------------

    @Test
    @DisplayName("only the finding's responsible confirms the correction plan")
    void onlyTheResponsiblePlans() {
        Finding finding = system.findings.findByInspection(close("30")).getFirst();
        assertThat(finding.responsible()).isEqualTo(owner.id());

        assertThatThrownBy(() -> system.planCorrectiveAction.plan(finding.id(), "fix", PartyId.of("executor"),
                system.clock.today().plusDays(5))).hasMessageContaining("only the responsible");
        assertThat(finding.correctiveAction().plan()).isEmpty();

        system.planAsResponsible(finding.id(), "fix", PartyId.of("executor"), system.clock.today().plusDays(5));
        assertThat(system.auditTrail.withAction(AuditAction.CORRECTIVE_ACTION_PLANNED)).singleElement()
                .satisfies(entry -> assertThat(((Actor.User) entry.actor()).partyId()).isEqualTo(owner.id()));
    }

    // --- Rectification and certificates -------------------------------------------------------

    @Test
    @DisplayName("a rejection that comes back with other reasons suspends the certificate until corrected again")
    void aReappearingRejectionSuspendsTheCertificate() {
        InspectionId id = close("30");
        Finding finding = system.findings.findByInspection(id).getFirst();
        correct(finding);
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));

        rectify(id, "0");   // still REJECTED, now too cold instead of too hot

        Finding after = system.findings.require(finding.id());
        assertThat(after.pendingNonConformity()).isTrue();
        assertThat(after.correctiveActions()).hasSize(2);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).singleElement()
                .isInstanceOf(SuspensionCause.NonConformity.class);

        correct(after);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    @DisplayName("repeating the close completes a closure whose findings were never registered")
    void aRepeatedCloseRegistersMissingFindings() {
        InspectionId id = start();
        system.recordAnswer.record(id, TEMPERATURE, Measurement.of("30", "c"));
        system.inspections.require(id).close(inspector.id(), system.clock.now());   // e.g. failure after saving
        assertThat(system.findings.findByInspection(id)).isEmpty();

        var retry = system.closeInspection.close(id);

        assertThat(retry.alreadyClosed()).isTrue();
        assertThat(system.findings.findByInspection(id)).extracting(Finding::criterionId)
                .containsExactlyInAnyOrder(TEMPERATURE, DOCUMENTATION);
        system.closeInspection.close(id);
        assertThat(system.findings.findByInspection(id)).hasSize(2);
    }

    // --- Issuance -----------------------------------------------------------------------------

    @Test
    @DisplayName("an inspection superseded by a later one of the same asset cannot back a certificate")
    void aSupersededInspectionCannotBackACertificate() {
        InspectionId older = close("5");
        system.clock.advanceDays(1);
        InspectionId newer = close("30");

        var decision = system.issueCertificate.issue(older);

        assertThat(decision).isInstanceOfSatisfying(IssuanceDecision.Blocked.class, blocked ->
                assertThat(blocked.blockers()).contains(new IssuanceBlocker.SupersededInspection(newer)));
        assertThat(system.evaluateEligibility.blockersFor(older))
                .contains(new IssuanceBlocker.SupersededInspection(newer));
        assertThat(system.certificates.findAll()).isEmpty();
    }

    @Test
    @DisplayName("once a certificate expired, the next one is a renewal linked to it, never an unrelated issue")
    void afterExpiryTheAssetIsRenewedNotReissued() {
        Certificate first = issuedCertificate(system.issueCertificate.issue(close("5")));
        system.clock.advanceDays(400);
        system.expireCertificates.sweep();
        InspectionId fresh = close("5");

        assertThatThrownBy(() -> system.issueCertificate.issue(fresh)).hasMessageContaining("renew it");
        assertThat(system.certificates.findAll()).hasSize(1);

        Certificate renewed = issuedCertificate(system.renewCertificate.renew(fresh));
        assertThat(renewed.previousCertificateId()).contains(first.id());
    }

    // --- Audit, encapsulation and reports -----------------------------------------------------

    @Test
    @DisplayName("a party is audited as a party and a refused publication is not audited as a publication")
    void auditEntriesDescribeWhatHappened() {
        assertThat(system.auditTrail.entriesFor(AuditedElementRef.party(inspector.id().value()),
                AuditAction.PARTY_REGISTERED)).hasSize(1);
        assertThat(system.auditTrail.entriesFor(AuditedElementRef.asset(inspector.id().value()))).isEmpty();

        var schema = system.createSchema.create("Factory inspection", Set.of(AssetType.FACTORY));
        system.openDraft.open(schema.id());
        assertThat(system.publishSchemaVersion.publish(schema.id()).published()).isFalse();
        var element = AuditedElementRef.schema(schema.id().value());
        assertThat(system.auditTrail.entriesFor(element, AuditAction.SCHEMA_VERSION_PUBLISHED)).isEmpty();
        assertThat(system.auditTrail.entriesFor(element, AuditAction.SCHEMA_PUBLICATION_REFUSED)).hasSize(1);
    }

    @Test
    @DisplayName("schemas and suspensions are only reachable through the domain services that guard them")
    void guardedConstructionAndSuspension() throws Exception {
        assertThat(InspectionSchema.class.getConstructors()).isEmpty();
        assertThat(Modifier.isPublic(Certificate.class.getDeclaredMethod("suspend", SuspensionCause.class,
                Instant.class).getModifiers())).isFalse();
        assertThat(Certificate.class.getMethods()).noneMatch(m -> Set.of("suspend", "resolveCause",
                "resolveCauses", "reactivateIfFullyResolved").contains(m.getName()));
    }

    @Test
    @DisplayName("the act distinguishes an original evidence reference from its rectified value")
    void theActDistinguishesRectifiedEvidence() {
        InspectionId id = close("5");
        String evidenceId = system.inspections.require(id).requireRecord(DOCUMENTATION).evidence().getFirst().id();
        system.rectifyClosedInspection.rectify(id, "wrong file linked",
                List.of(new Correction.EvidenceReferenceCorrection(DOCUMENTATION, evidenceId, "file://manual-v2")));

        var act = new GenerateInspectionAct(system.inspections, system.schemaCatalog).generate(id);
        var line = act.sections().getFirst().lines().stream()
                .filter(l -> l.criterionId().equals(DOCUMENTATION)).findFirst().orElseThrow();
        assertThat(line.evidenceReferences()).singleElement().isInstanceOfSatisfying(ReportedValue.Rectified.class,
                value -> {
                    assertThat(value.originalValue()).isEqualTo("file://manual");
                    assertThat(value.correctedValue()).isEqualTo("file://manual-v2");
                    assertThat(value.reason()).isEqualTo("wrong file linked");
                });
    }

    // --- helpers -------------------------------------------------------------------------------

    private <T> T asIntruder(java.util.function.Supplier<T> operation) {
        return system.actingAs(INTRUDER, operation);
    }

    private InspectionId assign() {
        return system.assignInspection.assign(asset.id(), inspector.id(), system.clock.today()).id();
    }

    private InspectionId start() {
        InspectionId id = assign();
        system.startInspection.start(id);
        return id;
    }

    private InspectionId close(String reading) {
        InspectionId id = start();
        system.recordAnswer.record(id, TEMPERATURE, Measurement.of(reading, "c"));
        system.recordAnswer.record(id, DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DOCUMENTATION, SAFETY_MANUAL, "file://manual");
        system.closeInspection.close(id);
        return id;
    }

    private void correct(Finding finding) {
        PartyId executor = PartyId.of("executor");
        system.planAsResponsible(finding.id(), "recalibrate", executor, system.clock.today().plusDays(10));
        system.actingAs(executor, () -> system.reportExecution.report(finding.id(), "done", List.of("file://proof")));
        system.verifyCorrectiveAction.verify(finding.id(), true, "within range");
        assertThat(system.findings.require(finding.id()).correctiveAction().status())
                .isEqualTo(CorrectiveActionStatus.CLOSED);
    }

    private void rectify(InspectionId id, String reading) {
        system.rectifyClosedInspection.rectify(id, "probe misread",
                List.of(new Correction.AnswerCorrection(TEMPERATURE, Measurement.of(reading, "c"))));
    }
}
