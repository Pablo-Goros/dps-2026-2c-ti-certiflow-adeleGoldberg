package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import static ar.edu.itba.dps.certification.support.Decisions.alreadyIssuedCertificate;
import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

class CertificationLifecycleIT {

    private FullSystem system;
    private AssetType laboratory;
    private Party inspector;
    private Party responsible;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        laboratory = AssetType.LABORATORY;
        system.publishLaboratorySchema(laboratory);
        inspector = system.person("Ana Perez");
        responsible = system.organization("Favaloro Foundation");
        asset = system.asset("Laboratory A", laboratory, responsible);
    }

    @Test
    @DisplayName("a fully approved inspection produces a certificate on request, and only one")
    void anApprovedInspectionCertifies() {
        InspectionId inspectionId = inspectAndClose("5", true);

        IssuanceDecision first = system.issueCertificate.issue(inspectionId);
        IssuanceDecision second = system.issueCertificate.issue(inspectionId);

        assertThat(issuedCertificate(first)).isNotNull();
        assertThat(alreadyIssuedCertificate(second)).isNotNull();
        assertThat(system.certificates.findAll()).hasSize(1);
        assertThat(alreadyIssuedCertificate(second))
                .isEqualTo(issuedCertificate(first).id());
    }

    @Test
    @DisplayName("a rejection blocks issuance until its correction is verified")
    void aRejectionBlocksIssuanceUntilCorrected() {
        InspectionId inspectionId = inspectAndClose("30", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();

        IssuanceDecision beforePlanning = system.issueCertificate.issue(inspectionId);
        assertThat(blockers(beforePlanning)).isNotEmpty();
        assertThat(blockers(beforePlanning))
                .anyMatch(IssuanceBlocker.UnverifiedRejection.class::isInstance)
                .anyMatch(IssuanceBlocker.UnplannedAction.class::isInstance);

        system.planCorrectiveAction.plan(finding.id(), "recalibrate the cooling unit",
                PartyId.of("executor"), LocalDate.parse("2026-04-01"));
        assertThat(blockers(system.issueCertificate.issue(inspectionId))).isNotEmpty();

        system.reportExecution.report(finding.id(), "unit recalibrated", List.of("file://photo.jpg"),
                PartyId.of("executor"));
        system.verifyCorrectiveAction.verify(finding.id(), true, "measured within range",
                inspector.id());

        assertThat(issuedCertificate(system.issueCertificate.issue(inspectionId))).isNotNull();
    }

    @Test
    @DisplayName("an observation does not block issuance but its action still has to be planned")
    void anObservationDoesNotBlockIssuance() {
        InspectionId inspectionId = inspectAndClose("20", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        IssuanceDecision decision = system.issueCertificate.issue(inspectionId);

        assertThat(issuedCertificate(decision)).isNotNull();
    }

    @Test
    @DisplayName("an action expiring suspends the certificate and closing it reactivates it")
    void anExpiringActionSuspendsAndItsClosureReactivates() {
        InspectionId inspectionId = inspectAndClose("20", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        Certificate certificate = issuedCertificate(system.issueCertificate.issue(inspectionId));
        Instant originalExpiry = certificate.validity().expiresAt();

        system.clock.advanceDays(45);
        system.expireActions.sweep();

        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);

        system.reportExecution.report(finding.id(), "ventilation improved",
                List.of("file://photo.jpg"), PartyId.of("executor"));
        system.verifyCorrectiveAction.verify(finding.id(), true, "airflow measured", inspector.id());

        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.validity().expiresAt()).isEqualTo(originalExpiry);
    }

    @Test
    @DisplayName("an action expiring with no certificate issued is recorded but suspends nothing")
    void anExpiringActionWithoutACertificateIsHarmless() {
        InspectionId inspectionId = inspectAndClose("20", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        system.clock.advanceDays(45);
        system.expireActions.sweep();

        assertThat(system.certificates.findAll()).isEmpty();
        assertThat(system.findings.require(finding.id()).correctiveAction().deadlineBreached())
                .isTrue();
        assertThat(blockers(system.issueCertificate.issue(inspectionId))).isNotEmpty();
    }

    @Test
    @DisplayName("a second inspection of an already certified asset cannot issue a parallel certificate")
    void anAssetCannotHoldTwoLiveCertificates() {
        InspectionId first = inspectAndClose("5", true);
        system.issueCertificate.issue(first);

        InspectionId second = inspectAndClose("5", true);
        IssuanceDecision decision = system.issueCertificate.issue(second);

        assertThat(blockers(decision)).isNotEmpty();
        assertThat(blockers(decision))
                .anyMatch(IssuanceBlocker.AssetAlreadyCertified.class::isInstance);
    }

    @Test
    @DisplayName("an inspection that is still open cannot be certified")
    void anOpenInspectionCannotBeCertified() {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);

        IssuanceDecision decision = system.issueCertificate.issue(inspectionId);

        assertThat(blockers(decision)).isNotEmpty();
        assertThat(blockers(decision))
                .anyMatch(IssuanceBlocker.InspectionNotClosed.class::isInstance);
        assertThat(system.certificates.findAll()).isEmpty();
    }

    private InspectionId inspectAndClose(String temperature, boolean documentationInOrder) {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION,
                YesNoAnswer.yes());
        if (documentationInOrder) {
            system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                    DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        }
        system.closeInspection.close(inspectionId);
        return inspectionId;
    }
}
