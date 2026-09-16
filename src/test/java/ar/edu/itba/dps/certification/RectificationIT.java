package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationChange;
import ar.edu.itba.dps.certification.domain.report.InspectionAct;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

class RectificationIT {

    private FullSystem system;
    private Party inspector;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        AssetType laboratory = AssetType.LABORATORY;
        system.publishLaboratorySchema(laboratory);
        inspector = system.person("Ana Perez");
        Party responsible = system.organization("Favaloro Foundation");
        asset = system.asset("Laboratory A", laboratory, responsible);
    }

    @Test
    @DisplayName("a rectification that clears the non-conformity voids the real obligation without inventing a correction")
    void clearingTheNonConformityVoidsTheRealObligation() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrate", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        rectifyTemperatureTo("5");

        Finding after = system.findings.require(finding.id());
        assertThat(after.obligationVoided()).isTrue();
        assertThat(after.voided()).isPresent();
        assertThat(after.correctiveAction().status()).isEqualTo(CorrectiveActionStatus.VOIDED);
        assertThat(after.correctiveAction().executions()).isEmpty();
        assertThat(after.correctiveAction().verifications()).isEmpty();
        assertThat(after.correctiveAction().blocksCertification()).isFalse();
    }

    @Test
    @DisplayName("a non-conformity that persists with a new result revises the finding and keeps its confirmed plan")
    void aPersistingNonConformityIsRevisedInPlace() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrate", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        rectifyTemperatureTo("20");

        Finding after = system.findings.require(finding.id());
        assertThat(system.findings.findByInspection(inspectionId)).hasSize(1);
        assertThat(after.result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(after.severity()).isEqualTo(Severity.LOW);
        assertThat(after.revisions()).singleElement().satisfies(revision -> {
            assertThat(revision.result()).isEqualTo(CriterionResult.OBSERVED);
            assertThat(revision.reason()).isNotBlank();
        });
        assertThat(after.correctiveAction().plan().orElseThrow().work()).isEqualTo("recalibrate");
        assertThat(after.correctiveAction().plan().orElseThrow().dueDate())
                .isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(after.obligationVoided()).isFalse();
    }

    @Test
    @DisplayName("a rectification revealing a rejection creates a finding and suspends the certificate at once")
    void aRevealedRejectionSuspendsTheCertificate() {
        InspectionId inspectionId = inspectAndClose("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(inspectionId));

        rectifyTemperatureTo("30");

        assertThat(system.findings.findByInspection(inspectionId)).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.result()).isEqualTo(CriterionResult.REJECTED);
                    assertThat(finding.correctiveAction().status())
                            .isEqualTo(CorrectiveActionStatus.PENDING_PLANNING);
                });
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).singleElement()
                .isInstanceOf(SuspensionCause.RectifiedRejection.class);
    }

    @Test
    @DisplayName("a rectification downgrading a rejection to an observation does not lift the suspension")
    void downgradingARejectionDoesNotLiftTheSuspension() {
        InspectionId inspectionId = inspectAndClose("5");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(inspectionId));
        rectifyTemperatureTo("30");

        rectifyTemperatureTo("20");

        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).hasSize(1);
    }

    @Test
    @DisplayName("a descriptive observation can be rectified and the act shows both texts")
    void aNoteCanBeRectified() {
        InspectionId inspectionId = assignAndStart();
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("5", "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        InspectionNote note = system.recordNote.record(inspectionId, Optional.empty(),
                "door was blocked");
        system.closeInspection.close(inspectionId);

        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(),
                "the door was a different room", List.of(
                        new Correction.NoteCorrection(note.id(), "corridor door was blocked")));

        InspectionAct act = new GenerateInspectionAct(system.inspections, system.schemaCatalog)
                .generate(inspectionId);
        assertThat(act.notes()).singleElement().satisfies(line -> {
            assertThat(line.text().rectified()).isTrue();
            assertThat(line.text().current()).isEqualTo("corridor door was blocked");
        });
        assertThat(act.rectifications()).hasSize(1);
    }

    @Test
    @DisplayName("an evidence reference recorded by mistake can be rectified")
    void anEvidenceReferenceCanBeRectified() {
        InspectionId inspectionId = assignAndStart();
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE, Measurement.of("5", "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        String evidenceId = system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://wrong-manual.pdf").id();
        system.closeInspection.close(inspectionId);

        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(),
                "the wrong file was attached", List.of(new Correction.EvidenceReferenceCorrection(
                        DomainWorld.DOCUMENTATION, evidenceId, "file://manual.pdf")));

        assertThat(system.inspections.require(inspectionId)
                .requireRecord(DomainWorld.DOCUMENTATION)
                .requireEvidence(evidenceId).reference()).isEqualTo("file://manual.pdf");
        assertThat(system.inspections.require(inspectionId).rectifications()).singleElement()
                .satisfies(rectification -> assertThat(rectification.changes()).singleElement()
                        .isInstanceOf(RectificationChange.EvidenceReferenceChanged.class));
    }

    private void rectifyTemperatureTo(String temperature) {
        system.rectifyClosedInspection.rectify(inspectionOf(), inspector.id(),
                "the probe was misread", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of(temperature, "c"))));
    }

    private InspectionId inspectionOf() {
        return system.inspections.findClosedByAsset(asset.id()).getFirst().id();
    }

    private InspectionId assignAndStart() {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        return inspectionId;
    }

    private InspectionId inspectAndClose(String temperature) {
        InspectionId inspectionId = assignAndStart();
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);
        return inspectionId;
    }
}
