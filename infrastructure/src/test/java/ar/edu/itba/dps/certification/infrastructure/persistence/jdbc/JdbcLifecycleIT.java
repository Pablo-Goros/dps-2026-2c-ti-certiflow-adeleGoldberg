package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.support.DomainWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real use cases over the real repositories, one transaction per use case as a request will
 * run them. What matters here is not the rules (the core tests them) but that they still hold
 * when every load builds the aggregate from the database instead of sharing an object in memory.
 */
class JdbcLifecycleIT {

    private DataSource dataSource;
    private JdbcSystem system;
    private Party inspector;
    private Asset asset;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabase.dataSource();
        system = new JdbcSystem(JdbcPersistence.open(dataSource));
        inspector = system.tx(() -> system.registerParty.register("Ana Perez", PartyKind.PERSON));
        system.actAs(inspector);
        Party organization = system.tx(() -> system.registerParty.register("Favaloro Foundation", PartyKind.ORGANIZATION));
        system.tx(system::publishLaboratorySchema);
        asset = system.tx(() -> system.registerAsset.register("Laboratory A", AssetType.LABORATORY,
                organization.id(), "Building 1", Map.of("room", "12"), JurisdictionId.of("REFERENCE")));
    }

    @Test
    @DisplayName("an inspection is carried out, its rejection corrected and the asset certified, and all of it survives a restart")
    void aWholeLifecycleIsPersisted() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.persistence.findings().findByInspection(inspectionId).getFirst();

        assertThat(blockers(system.tx(() -> system.issueCertificate.issue(inspectionId)))).isNotEmpty();
        assertThat(system.persistence.certificates().findAll()).isEmpty();

        system.actingAs(finding.responsible(), () -> system.tx(() -> system.planCorrectiveAction.plan(
                finding.id(), "recalibrate the cooling unit", PartyId.of("executor"), LocalDate.parse("2026-04-01"))));
        system.actingAs(PartyId.of("executor"), () -> system.tx(() -> system.reportExecution.report(
                finding.id(), "unit recalibrated", List.of("file://photo.jpg"))));
        system.actingAs(inspector.id(), () -> system.tx(() -> system.verifyCorrectiveAction.verify(
                finding.id(), true, "measured within range")));

        IssuanceDecision decision = system.tx(() -> system.issueCertificate.issue(inspectionId));
        Certificate issued = issuedCertificate(decision);

        JdbcPersistence afterRestart = JdbcPersistence.open(dataSource);
        assertThat(afterRestart.certificates().findAll()).hasSize(1);
        assertThat(afterRestart.certificates().findById(issued.id()).orElseThrow().status())
                .isEqualTo(CertificateStatus.VALID);
        assertThat(afterRestart.inspections().findById(inspectionId).orElseThrow().status().closed()).isTrue();
        assertThat(afterRestart.findings().findByInspection(inspectionId)).hasSize(1);
        assertThat(afterRestart.findings().findWithOpenActions()).isEmpty();
        assertThat(afterRestart.auditTrail().all()).extracting(AuditEntry::action)
                .contains(AuditAction.ASSET_REGISTERED, AuditAction.INSPECTION_CLOSED, AuditAction.FINDING_CREATED,
                        AuditAction.CORRECTIVE_ACTION_CLOSED, AuditAction.CERTIFICATE_ISSUED);
    }

    @Test
    @DisplayName("a rectification that reveals a rejection suspends the certificate in the same transaction")
    void aRectificationAndItsConsequencesAreCommittedTogether() {
        InspectionId inspectionId = inspectAndClose("5");
        Certificate certificate = issuedCertificate(system.tx(() -> system.issueCertificate.issue(inspectionId)));

        system.actingAs(inspector.id(), () -> system.tx(() -> system.rectifyClosedInspection.rectify(inspectionId,
                "the probe was misread", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of("30", "c"))))));

        JdbcPersistence afterRestart = JdbcPersistence.open(dataSource);
        assertThat(afterRestart.certificates().findById(certificate.id()).orElseThrow().status())
                .isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(afterRestart.findings().findByInspection(inspectionId)).hasSize(1);
        assertThat(afterRestart.inspections().wasRectified(inspectionId)).isTrue();
        assertThat(afterRestart.auditTrail().all()).extracting(AuditEntry::action)
                .contains(AuditAction.INSPECTION_RECTIFIED, AuditAction.CERTIFICATE_SUSPENDED);
        assertThat(afterRestart.inspections().findById(inspectionId).orElseThrow().pendingEvents()).isEmpty();
    }

    @Test
    @DisplayName("a refused operation leaves no trace: no state change and no audit entry")
    void aRefusedOperationLeavesNothingBehind() {
        InspectionId inspectionId = system.tx(() -> system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id());
        system.tx(() -> system.startInspection.start(inspectionId));
        int auditedBefore = system.persistence.auditTrail().all().size();
        Party intruder = system.tx(() -> system.registerParty.register("Laura Gomez", PartyKind.PERSON));
        int auditedAfterRegistering = system.persistence.auditTrail().all().size();
        assertThat(auditedAfterRegistering).isEqualTo(auditedBefore + 1);

        assertThatThrownBy(() -> system.actingAs(intruder.id(), () -> system.tx(() -> system.recordAnswer.record(
                inspectionId, DomainWorld.TEMPERATURE, Measurement.of("5", "c")))))
                .isInstanceOf(DomainException.class);

        assertThat(system.persistence.auditTrail().all()).hasSize(auditedAfterRegistering);
        assertThat(system.persistence.inspections().findById(inspectionId).orElseThrow().currentEvaluations())
                .isEmpty();
    }

    private InspectionId inspectAndClose(String temperature) {
        InspectionId inspectionId = system.tx(() -> system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id());
        system.tx(() -> system.startInspection.start(inspectionId));
        system.tx(() -> system.recordAnswer.record(inspectionId, DomainWorld.TEMPERATURE,
                Measurement.of(temperature, "c")));
        system.tx(() -> system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes()));
        system.tx(() -> system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf"));
        system.tx(() -> system.closeInspection.close(inspectionId));
        return inspectionId;
    }
}
