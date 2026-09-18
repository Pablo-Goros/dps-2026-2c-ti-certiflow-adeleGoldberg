package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

class RecurringNonConformityIT {

    private FullSystem system;
    private Party inspector;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishLaboratorySchema(AssetType.LABORATORY);
        inspector = system.person("Ana Perez");
        asset = system.asset("Laboratory A", AssetType.LABORATORY,
                system.organization("Favaloro Foundation"));
    }

    @Test
    @DisplayName("a rectification that brings back a voided non-conformity suspends the certificate")
    void aVoidedNonConformityThatComesBackSuspendsTheCertificate() {
        InspectionId id = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(id).getFirst();
        rectify(id, "5");
        assertThat(system.findings.require(finding.id()).obligationVoided()).isTrue();
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));

        rectify(id, "30");

        Finding after = system.findings.require(finding.id());
        assertThat(after.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(after.revisions()).isNotEmpty();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).hasSize(1);
    }

    @Test
    @DisplayName("the voiding is kept as an antecedent rather than erased")
    void theVoidingRemainsInTheHistory() {
        InspectionId id = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(id).getFirst();
        rectify(id, "5");

        rectify(id, "30");

        Finding after = system.findings.require(finding.id());
        assertThat(after.voided()).isPresent();
        assertThat(after.correctiveActions()).hasSize(2);
        assertThat(after.correctiveActions().getFirst().status())
                .isEqualTo(CorrectiveActionStatus.VOIDED);
        assertThat(after.correctiveAction().status())
                .isEqualTo(CorrectiveActionStatus.PENDING_PLANNING);
    }

    @Test
    @DisplayName("a correction verified before the rejection appeared does not cover it")
    void aCorrectionConcludedBeforeTheRejectionDoesNotCoverIt() {
        InspectionId id = inspectAndClose("20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "ventilar", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        system.reportExecution.report(finding.id(), "hecho", List.of("f://a.jpg"),
                PartyId.of("executor"));
        system.clock.advanceDays(1);
        system.verifyCorrectiveAction.verify(finding.id(), true, "medido en rango", inspector.id());
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));

        system.clock.advanceDays(1);
        rectify(id, "30");

        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(system.findings.require(finding.id()).blocksCertification()).isTrue();
    }

    @Test
    @DisplayName("correcting the new occurrence reactivates the certificate without a new inspection")
    void theNewOccurrenceIsCorrectedAndTheCertificateComesBack() {
        InspectionId id = inspectAndClose("20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        correctAndClose(finding, "ventilar");
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(id));
        rectify(id, "30");
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);

        correctAndClose(finding, "recalibrar el sensor");

        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.unresolvedCauses()).isEmpty();
        assertThat(system.findings.require(finding.id()).correctiveActions()).hasSize(2);
        assertThat(system.findings.require(finding.id()).blocksCertification()).isFalse();
    }

    @Test
    @DisplayName("the plan of the concluded action is not touched by the new occurrence")
    void theEarlierPlanSurvivesUntouched() {
        InspectionId id = inspectAndClose("20");
        Finding finding = system.findings.findByInspection(id).getFirst();
        correctAndClose(finding, "ventilar");

        rectify(id, "30");

        Finding after = system.findings.require(finding.id());
        assertThat(after.correctiveActions().getFirst().plan().orElseThrow().work())
                .isEqualTo("ventilar");
        assertThat(after.correctiveActions().getFirst().status())
                .isEqualTo(CorrectiveActionStatus.CLOSED);
        assertThat(after.correctiveAction().plan()).isEmpty();
    }

    private void correctAndClose(Finding finding, String work) {
        system.planCorrectiveAction.plan(finding.id(), work, PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        system.reportExecution.report(finding.id(), "hecho", List.of("f://a.jpg"),
                PartyId.of("executor"));
        system.verifyCorrectiveAction.verify(finding.id(), true, "verificado", inspector.id());
    }

    @Test
    @DisplayName("a correction verified after the rejection appeared does cover it")
    void aCorrectionConcludedAfterTheRejectionCoversIt() {
        InspectionId id = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(id).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrar", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        system.reportExecution.report(finding.id(), "hecho", List.of("f://a.jpg"),
                PartyId.of("executor"));
        system.clock.advanceDays(1);
        system.verifyCorrectiveAction.verify(finding.id(), true, "en rango", inspector.id());

        assertThat(system.findings.require(finding.id()).blocksCertification()).isFalse();
        assertThat(issuedCertificate(system.issueCertificate.issue(id))).isNotNull();
    }

    private void rectify(InspectionId id, String temp) {
        system.rectifyClosedInspection.rectify(id, inspector.id(), "relectura del instrumento",
                List.of(new Correction.AnswerCorrection(DomainWorld.TEMPERATURE,
                        Measurement.of(temp, "c"))));
    }

    private InspectionId inspectAndClose(String temp) {
        InspectionId id = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.TEMPERATURE, Measurement.of(temp, "c"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL,
                "f://manual.pdf");
        system.closeInspection.close(id);
        return id;
    }
}
