package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.report.usecase.GenerateCertificateReport;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateFindingsSummary;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.report.CertificateReport;
import ar.edu.itba.dps.certification.domain.report.FindingsSummary;
import ar.edu.itba.dps.certification.domain.report.InspectionAct;
import ar.edu.itba.dps.certification.domain.report.IssuanceAttemptReport;
import ar.edu.itba.dps.certification.domain.report.ReportedValue;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.LocalDate;
import java.util.List;

class ReportingIT {

    private FullSystem system;
    private GenerateInspectionAct generateAct;
    private GenerateFindingsSummary generateSummary;
    private GenerateCertificateReport generateCertificateReport;
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
        generateAct = new GenerateInspectionAct(system.inspections, system.schemaCatalog);
        generateSummary = new GenerateFindingsSummary(system.findingQuery);
        generateCertificateReport = new GenerateCertificateReport(system.certificates,
                system.inspections, system.findingQuery);
    }

    @Test
    @DisplayName("the act distinguishes the original reading from the rectified one")
    void theActDistinguishesOriginalFromRectified() {
        InspectionId inspectionId = inspectAndClose("30");
        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(),
                "the probe was misread", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of("5", "c"))));

        InspectionAct act = generateAct.generate(inspectionId);

        InspectionAct.ActCriterionLine temperature = lineFor(act, "TEMP");
        assertThat(temperature.answer().rectified()).isTrue();
        assertThat(temperature.answer()).isInstanceOfSatisfying(ReportedValue.Rectified.class,
                value -> {
                    assertThat(value.originalValue()).isEqualTo("30 c");
                    assertThat(value.correctedValue()).isEqualTo("5 c");
                    assertThat(value.reason()).isEqualTo("the probe was misread");
                });
        assertThat(temperature.result().orElseThrow()).isInstanceOfSatisfying(
                ReportedValue.Rectified.class, value -> {
                    assertThat(value.originalValue()).isEqualTo(CriterionResult.REJECTED.name());
                    assertThat(value.correctedValue()).isEqualTo(CriterionResult.APPROVED.name());
                });
        assertThat(act.carriesRectifications()).isTrue();
    }

    @Test
    @DisplayName("an unrectified value is reported as original")
    void anUnrectifiedValueIsReportedAsOriginal() {
        InspectionId inspectionId = inspectAndClose("5");

        InspectionAct act = generateAct.generate(inspectionId);

        assertThat(lineFor(act, "TEMP").answer().rectified()).isFalse();
        assertThat(act.carriesRectifications()).isFalse();
        assertThat(act.rectifications()).isEmpty();
    }

    @Test
    @DisplayName("the act carries the asset as it was at the start, not as it is now")
    void theActCarriesTheFrozenAsset() {
        InspectionId inspectionId = inspectAndClose("5");
        Party newResponsible = system.organization("Merk Laboratories");
        system.changeAssetResponsible.change(asset.id(), newResponsible.id());

        InspectionAct act = generateAct.generate(inspectionId);

        assertThat(act.asset().orElseThrow().responsible().partyId()).isEqualTo(responsible.id());
    }

    @Test
    @DisplayName("the findings summary lists motives, severity, evidence and the associated action")
    void theFindingsSummaryIsComplete() {
        InspectionId inspectionId = inspectAndClose("30");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "recalibrate the cooling unit",
                PartyId.of("executor"), LocalDate.parse("2026-04-01"));
        system.reportExecution.report(finding.id(), "recalibrated", List.of("file://photo.jpg"),
                PartyId.of("executor"));
        system.verifyCorrectiveAction.verify(finding.id(), false, "still above range",
                inspector.id());

        FindingsSummary summary = generateSummary.generate(inspectionId);

        assertThat(summary.lines()).singleElement().satisfies(line -> {
            assertThat(line.criterionId()).isEqualTo(DomainWorld.TEMPERATURE);
            assertThat(line.result()).isEqualTo(CriterionResult.REJECTED);
            assertThat(line.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(line.motives()).isNotEmpty();
            assertThat(line.responsible()).isEqualTo(responsible.id());
            assertThat(line.action().dueDate()).contains(LocalDate.parse("2026-04-01"));
            assertThat(line.action().verifications()).singleElement()
                    .satisfies(verification -> assertThat(verification.satisfactory()).isFalse());
        });
    }

    @Test
    @DisplayName("a blocked issuance still produces the act and the summary and never a certificate")
    void aBlockedIssuanceStillProducesReports() {
        InspectionId inspectionId = inspectAndClose("30");

        IssuanceDecision decision = system.issueCertificate.issue(inspectionId);
        IssuanceAttemptReport attempt = generateCertificateReport.reportBlockedAttempt(inspectionId,
                blockers(decision));

        assertThat(attempt.certified()).isFalse();
        assertThat(attempt.certificateId()).isEmpty();
        assertThat(attempt.blockingReasons()).isNotEmpty();
        assertThat(generateAct.generate(inspectionId).sections()).isNotEmpty();
        assertThat(generateSummary.generate(inspectionId).lines()).isNotEmpty();
        assertThat(system.certificates.findAll()).isEmpty();
    }

    @Test
    @DisplayName("the certificate report lists the commitments still pending and their deadlines")
    void theCertificateReportListsPendingCommitments() {
        InspectionId inspectionId = inspectAndClose("20");
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planCorrectiveAction.plan(finding.id(), "improve ventilation", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        IssuanceDecision decision = system.issueCertificate.issue(inspectionId);

        CertificateReport report = generateCertificateReport.generate(
                issuedCertificate(decision).id());

        assertThat(report.pendingCommitments()).singleElement().satisfies(commitment -> {
            assertThat(commitment.criterionId()).isEqualTo(DomainWorld.TEMPERATURE);
            assertThat(commitment.work()).isEqualTo("improve ventilation");
            assertThat(commitment.dueDate()).isEqualTo(LocalDate.parse("2026-04-01"));
        });
        assertThat(report.backingInspectionWasRectified()).isFalse();
        assertThat(report.unresolvedSuspensionCauses()).isEmpty();
    }

    @Test
    @DisplayName("the act attributes a twice-rectified answer to the rectification that produced it")
    void successiveRectificationsAreAttributedToTheLastOne() {
        InspectionId inspectionId = inspectAndClose("30");
        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(),
                "the probe read one digit too high", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of("20", "c"))));
        system.rectifyClosedInspection.rectify(inspectionId, inspector.id(),
                "the calibration sheet gave the final figure", List.of(new Correction.AnswerCorrection(
                        DomainWorld.TEMPERATURE, Measurement.of("12", "c"))));

        InspectionAct act = generateAct.generate(inspectionId);

        assertThat(lineFor(act, "TEMP").answer()).isInstanceOfSatisfying(
                ReportedValue.Rectified.class, value -> {
                    assertThat(value.originalValue()).isEqualTo("30 c");
                    assertThat(value.correctedValue()).isEqualTo("12 c");
                    assertThat(value.reason()).isEqualTo("the calibration sheet gave the final figure");
                    assertThat(value.rectificationId())
                            .isEqualTo(act.rectifications().getLast().id());
                });
        assertThat(act.rectifications()).hasSize(2);
    }

    private InspectionAct.ActCriterionLine lineFor(InspectionAct act, String criterionId) {
        return act.sections().stream()
                .flatMap(section -> section.lines().stream())
                .filter(line -> line.criterionId().value().equals(criterionId))
                .findFirst()
                .orElseThrow();
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
