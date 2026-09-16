package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;
import java.util.List;

class RenewalIT {

    private FullSystem system;
    private Party inspector;
    private Party responsible;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        AssetType laboratory = AssetType.LABORATORY;
        system.publishLaboratorySchema(laboratory);
        inspector = system.person("Ana Perez");
        responsible = system.organization("Favaloro Foundation");
        asset = system.asset("Laboratory A", laboratory, responsible);
    }

    @Test
    @DisplayName("a certificate expires when its validity lapses and the sweep runs")
    void aCertificateExpiresWhenItsValidityLapses() {
        Certificate certificate = issueFor(inspectAndClose("5"));

        system.clock.advanceDays(400);
        List<Certificate> expired = system.expireCertificates.sweep();

        assertThat(expired).containsExactly(certificate);
        assertThat(certificate.status()).isEqualTo(CertificateStatus.EXPIRED);
        assertThat(system.expireCertificates.sweep()).isEmpty();
    }

    @Test
    @DisplayName("renewal is refused while the previous certificate is still valid")
    void renewalIsRefusedBeforeThepreviousExpires() {
        issueFor(inspectAndClose("5"));

        assertThatThrownBy(() -> system.renewCertificate.renew(inspectAndClose("5")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has not expired yet");
    }

    @Test
    @DisplayName("renewal issues a new certificate linked to the one it replaces")
    void renewalLinksToThePreviousCertificate() {
        Certificate first = issueFor(inspectAndClose("5"));
        system.clock.advanceDays(400);
        system.expireCertificates.sweep();

        IssuanceDecision decision =
                system.renewCertificate.renew(inspectAndClose("5"));
        Certificate renewed = issuedCertificate(decision);
        assertThat(renewed.previousCertificateId()).contains(first.id());
        assertThat(renewed.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(renewed.backingInspectionId()).isNotEqualTo(first.backingInspectionId());
    }

    @Test
    @DisplayName("an overdue action of an earlier inspection neither blocks the renewal nor suspends the new certificate")
    void earlierInspectionsActionsDoNotAffectTheRenewal() {
        InspectionId firstInspection = inspectAndClose("20");
        Finding oldFinding = system.findings.findByInspection(firstInspection).getFirst();
        system.planCorrectiveAction.plan(oldFinding.id(), "improve ventilation",
                PartyId.of("executor"), LocalDate.parse("2026-04-01"));
        issueFor(firstInspection);

        system.clock.advanceDays(400);
        system.expireActions.sweep();
        system.expireCertificates.sweep();

        IssuanceDecision decision =
                system.renewCertificate.renew(inspectAndClose("5"));
        Certificate renewed = issuedCertificate(decision);
        assertThat(renewed.status()).isEqualTo(CertificateStatus.VALID);

        system.clock.advanceDays(30);
        system.expireActions.sweep();

        assertThat(renewed.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(renewed.unresolvedCauses()).isEmpty();
        assertThat(system.findings.require(oldFinding.id()).correctiveAction().deadlineBreached())
                .isTrue();
    }

    @Test
    @DisplayName("renewal is refused when the asset never held a certificate")
    void renewalNeedsSomethingToRenew() {
        assertThatThrownBy(() -> system.renewCertificate.renew(inspectAndClose("5")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no certificate to renew");
    }

    private Certificate issueFor(InspectionId inspectionId) {
        IssuanceDecision decision = system.issueCertificate.issue(inspectionId);
        return issuedCertificate(decision);
    }

    private InspectionId inspectAndClose(String temperature) {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);
        return inspectionId;
    }
}
