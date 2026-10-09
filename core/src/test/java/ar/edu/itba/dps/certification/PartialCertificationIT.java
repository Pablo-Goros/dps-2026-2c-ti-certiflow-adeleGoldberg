package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.CertificationPlan;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.report.CertificateReport;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ar.edu.itba.dps.certification.support.Decisions.alreadyIssuedCertificate;
import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartialCertificationIT {

    private static final Subsystem ELECTRICAL = DomainWorld.ELECTRICAL;
    private static final Subsystem PRESSURE = DomainWorld.PRESSURE;

    private FullSystem system;
    private Party inspector;
    private Party responsible;
    private Asset asset;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        responsible = system.organization("Favaloro Foundation");
        asset = system.registerAsset.register("Central Facility", AssetType.FACILITY,
                responsible.id(), "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(ELECTRICAL, PRESSURE), JurisdictionId.of("REFERENCE"));
    }

    @Test
    @DisplayName("each subsystem gets its own certificate and together they derive a global one")
    void everySubsystemCertifiedDerivesAGlobalCertificate() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));

        assertThat(electrical.scope()).isEqualTo(CertificateScope.of(ELECTRICAL));
        assertThat(pressure.scope()).isEqualTo(CertificateScope.of(PRESSURE));
        assertThat(electrical.id()).isNotEqualTo(pressure.id());
        assertThat(system.certificates.findByBackingInspection(inspectionId)).hasSize(2);

        var derivation = system.deriveGlobalCertificate.deriveFor(inspectionId);
        var global = derivation.derivedCertificate().orElseThrow();
        assertThat(global.coveredSubsystems()).containsExactly(ELECTRICAL, PRESSURE);
        assertThat(global.derivedFrom()).containsExactly(electrical.id(), pressure.id());
        assertThat(global.assetId()).isEqualTo(asset.id());
    }

    @Test
    @DisplayName("a schema split into subsystems refuses a directly issued global certificate")
    void aGlobalCertificateCannotBeIssuedDirectly() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        assertThatThrownBy(() -> system.issueCertificate.issue(inspectionId))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("certifies the subsystems")
                .hasMessageContaining("derive the global certificate from them");
    }

    @Test
    @DisplayName("a rejection in one subsystem leaves the other one certifiable")
    void aRejectionIsConfinedToItsSubsystem() {
        InspectionId inspectionId = inspectAndClose("hazardous", "clean", true);

        assertThat(blockers(system.issueCertificate.issuePartial(inspectionId, ELECTRICAL)))
                .anyMatch(IssuanceBlocker.UnverifiedRejection.class::isInstance);
        assertThat(issuedCertificate(system.issueCertificate.issuePartial(inspectionId, PRESSURE)))
                .isNotNull();

        var derivation = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe())
                .contains("electrical installation has no certificate");
    }

    @Test
    @DisplayName("a criterion outside every subsystem blocks all of them")
    void aTransversalRejectionBlocksEverySubsystem() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", false);

        assertThat(blockers(system.issueCertificate.issuePartial(inspectionId, ELECTRICAL)))
                .anyMatch(IssuanceBlocker.UnverifiedRejection.class::isInstance);
        assertThat(blockers(system.issueCertificate.issuePartial(inspectionId, PRESSURE)))
                .anyMatch(IssuanceBlocker.UnverifiedRejection.class::isInstance);
    }

    @Test
    @DisplayName("each partial certificate keeps a validity of its own")
    void eachPartialKeepsAnIndependentValidity() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        system.clock.advanceDays(30);
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));

        assertThat(pressure.validity().issuedAt()).isAfter(electrical.validity().issuedAt());
        assertThat(pressure.validity().expiresAt()).isAfter(electrical.validity().expiresAt());

        var global = system.deriveGlobalCertificate.deriveFor(inspectionId)
                .derivedCertificate().orElseThrow();
        assertThat(global.validity().issuedAt()).isEqualTo(pressure.validity().issuedAt());
        assertThat(global.validity().expiresAt()).isEqualTo(electrical.validity().expiresAt());
    }

    @Test
    @DisplayName("an overdue action of one subsystem suspends only its certificate")
    void anOverdueActionSuspendsOnlyItsSubsystem() {
        InspectionId inspectionId = inspectAndClose("untidy", "clean", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planAsResponsible(finding.id(), "tidy the switchboard", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));

        system.clock.advanceDays(45);
        system.expireActions.sweep();

        assertThat(electrical.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(pressure.status()).isEqualTo(CertificateStatus.VALID);

        var derivation = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe()).contains("electrical installation is suspended");
    }

    @Test
    @DisplayName("a partial certificate reports only the commitments of its own subsystem")
    void aPartialReportsOnlyItsOwnCommitments() {
        InspectionId inspectionId = inspectAndClose("untidy", "clean", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planAsResponsible(finding.id(), "tidy the switchboard", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));

        CertificateReport electricalReport = system.generateCertificateReport.generate(electrical.id());
        CertificateReport pressureReport = system.generateCertificateReport.generate(pressure.id());

        assertThat(electricalReport.scope()).isEqualTo(CertificateScope.of(ELECTRICAL));
        assertThat(electricalReport.pendingCommitments())
                .extracting(CertificateReport.PendingCommitment::criterionId)
                .containsExactly(DomainWorld.ELECTRICAL_WIRING);
        assertThat(pressureReport.pendingCommitments()).isEmpty();
    }

    @Test
    @DisplayName("a subsystem the frozen version does not declare cannot be certified")
    void anUndeclaredSubsystemCannotBeCertified() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        assertThatThrownBy(() -> system.issueCertificate
                .issuePartial(inspectionId, Subsystem.of("fire protection")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not certify subsystem fire protection");
    }

    @Test
    @DisplayName("an asset without one of the schema's subsystems certifies over the ones it has")
    void anAssetThatLacksASubsystemStillGetsAGlobalCertificate() {
        Asset electricalOnly = system.registerAsset.register("Annex", AssetType.FACILITY,
                responsible.id(), "Building 2", Map.of("room", "3", "purpose", "storage"),
                Set.of(ELECTRICAL), JurisdictionId.of("REFERENCE"));
        InspectionId inspectionId = system.assignInspection
                .assign(electricalOnly.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);

        assertThat(system.findings.findByInspection(inspectionId))
                .extracting(Finding::criterionId)
                .doesNotContain(DomainWorld.PRESSURE_VALVES);

        assertThat(issuedCertificate(system.issueCertificate.issuePartial(inspectionId, ELECTRICAL)))
                .isNotNull();
        assertThatThrownBy(() -> system.issueCertificate.issuePartial(inspectionId, PRESSURE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not certify subsystem pressure system");

        var global = system.deriveGlobalCertificate.deriveFor(inspectionId)
                .derivedCertificate().orElseThrow();
        assertThat(global.coveredSubsystems()).containsExactly(ELECTRICAL);
    }

    @Test
    @DisplayName("a criterion of a part the asset lacks cannot be answered and is not evaluated")
    void aCriterionOfAnAbsentPartIsNotApplicable() {
        Asset electricalOnly = system.registerAsset.register("Annex", AssetType.FACILITY,
                responsible.id(), "Building 2", Map.of("room", "3", "purpose", "storage"),
                Set.of(ELECTRICAL), JurisdictionId.of("REFERENCE"));
        InspectionId inspectionId = system.assignInspection
                .assign(electricalOnly.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);

        assertThatThrownBy(() -> system.recordAnswer.record(inspectionId,
                DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("evaluates a part that asset")
                .hasMessageContaining("does not have");

        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        var closure = system.closeInspection.close(inspectionId);

        assertThat(closure.evaluations()).containsOnlyKeys(DomainWorld.ELECTRICAL_WIRING,
                DomainWorld.DOCUMENTATION);
        assertThat(closure.nonApproved()).isEmpty();

        var act = system.generateInspectionAct.generate(inspectionId);
        var pressureLine = act.sections().stream().flatMap(section -> section.lines().stream())
                .filter(line -> line.criterionId().equals(DomainWorld.PRESSURE_VALVES))
                .findFirst().orElseThrow();
        assertThat(pressureLine.applicable()).isFalse();
        assertThat(pressureLine.result()).isEmpty();
        assertThat(pressureLine.missingEvidence()).isEmpty();
    }

    @Test
    @DisplayName("an asset that declares no part is certified as a whole by every criterion")
    void anAssetWithoutDeclaredPartsIsCertifiedAsAWhole() {
        Asset whole = system.registerAsset.register("Warehouse", AssetType.FACILITY,
                responsible.id(), "Building 3", Map.of("room", "1", "purpose", "storage"),
                Set.of(), JurisdictionId.of("REFERENCE"));
        InspectionId inspectionId = system.assignInspection
                .assign(whole.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.BUILDING_EXITS,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        var closure = system.closeInspection.close(inspectionId);

        assertThat(closure.evaluations()).hasSize(4);

        Certificate certificate = issuedCertificate(system.issueCertificate.issue(inspectionId));
        assertThat(certificate.scope()).isEqualTo(CertificateScope.global());
        assertThatThrownBy(() -> system.issueCertificate.issuePartial(inspectionId, ELECTRICAL))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not certify subsystem electrical installation");

        var derivation = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe()).contains("declares no part");
        assertThat(system.certificates.findByBackingInspection(inspectionId,
                CertificateScope.global())).contains(certificate);
    }

    @Test
    @DisplayName("the summary tells the caller which issuing operation applies")
    void theSummaryAnswersWhichOperationApplies() {
        InspectionId byParts = inspectAndClose("clean", "clean", true);
        Asset whole = system.registerAsset.register("Warehouse", AssetType.FACILITY,
                responsible.id(), "Building 3", Map.of("room", "1", "purpose", "storage"),
                Set.of(), JurisdictionId.of("REFERENCE"));
        InspectionId asAWhole = system.assignInspection
                .assign(whole.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(asAWhole);
        system.recordAnswer.record(asAWhole, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        system.recordAnswer.record(asAWhole, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(asAWhole, DomainWorld.BUILDING_EXITS, OptionAnswer.of("clean"));
        system.recordAnswer.record(asAWhole, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(asAWhole, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(asAWhole);

        assertThat(system.inspections.summaryOf(byParts).certificationPlan())
                .isEqualTo(new CertificationPlan.ByParts(Set.of(ELECTRICAL, PRESSURE)));
        assertThat(system.inspections.summaryOf(asAWhole).certificationPlan())
                .isEqualTo(new CertificationPlan.AsAWhole());

        for (InspectionId id : List.of(byParts, asAWhole)) {
            switch (system.inspections.summaryOf(id).certificationPlan()) {
                case CertificationPlan.AsAWhole ignored ->
                        assertThat(issuedCertificate(system.issueCertificate.issue(id))).isNotNull();
                case CertificationPlan.ByParts byPartsPlan -> {
                    byPartsPlan.subsystems().forEach(subsystem ->
                            assertThat(issuedCertificate(
                                    system.issueCertificate.issuePartial(id, subsystem))).isNotNull());
                    assertThat(system.deriveGlobalCertificate.deriveFor(id).derivedCertificate())
                            .isPresent();
                }
            }
        }
    }

    @Test
    @DisplayName("none of the loading operations accept a criterion of an absent part")
    void noLoadingOperationAcceptsAnAbsentPart() {
        Asset electricalOnly = system.registerAsset.register("Annex", AssetType.FACILITY,
                responsible.id(), "Building 2", Map.of("room", "3", "purpose", "storage"),
                Set.of(ELECTRICAL), JurisdictionId.of("REFERENCE"));
        InspectionId id = system.assignInspection
                .assign(electricalOnly.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);

        assertThatThrownBy(() -> system.recordAnswer.record(id, DomainWorld.PRESSURE_VALVES,
                OptionAnswer.of("clean"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> system.removeAnswer.remove(id, DomainWorld.PRESSURE_VALVES))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> system.attachEvidence.attach(id, DomainWorld.PRESSURE_VALVES,
                "any", "file://x.pdf")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> system.removeEvidence.remove(id, DomainWorld.PRESSURE_VALVES,
                "any")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("asking twice for the same subsystem reports the certificate it already has")
    void aRepeatedPartialRequestReportsTheExistingCertificate() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        Certificate first = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        var second = system.issueCertificate.issuePartial(inspectionId, ELECTRICAL);

        assertThat(alreadyIssuedCertificate(second)).isEqualTo(first.id());
        assertThat(system.certificates.findByBackingInspection(inspectionId)).hasSize(1);
    }

    @Test
    @DisplayName("a subsystem certificate cannot be renewed before it expires or without a predecessor")
    void renewingAPartialNeedsAnExpiredPredecessor() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);
        system.issueCertificate.issuePartial(inspectionId, ELECTRICAL);

        assertThatThrownBy(() -> system.renewCertificate.renewPartial(inspectionId, ELECTRICAL))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has not expired yet");
        assertThatThrownBy(() -> system.renewCertificate.renewPartial(inspectionId, PRESSURE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has no certificate to renew");
    }

    @Test
    @DisplayName("an overdue action of a transversal criterion suspends every subsystem")
    void anOverdueTransversalActionSuspendsEverySubsystem() {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.no());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);

        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        assertThat(finding.criterionId()).isEqualTo(DomainWorld.DOCUMENTATION);
        system.planAsResponsible(finding.id(), "reissue the manual", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));

        system.clock.advanceDays(45);
        system.expireActions.sweep();

        assertThat(electrical.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(pressure.status()).isEqualTo(CertificateStatus.SUSPENDED);

        var derivation = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(derivation.derivedCertificate()).isEmpty();
        assertThat(derivation.describe())
                .contains("electrical installation is suspended")
                .contains("pressure system is suspended");
    }

    @Test
    @DisplayName("a null subsystem is refused rather than treated as the whole asset")
    void aNullSubsystemIsRefused() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);

        assertThatThrownBy(() -> system.issueCertificate.issuePartial(inspectionId, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("subsystem");
        assertThatThrownBy(() -> system.evaluateEligibility.blockersFor(inspectionId, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("deriving for an inspection that does not exist says so")
    void derivingForAnUnknownInspectionFails() {
        assertThatThrownBy(() -> system.deriveGlobalCertificate
                .deriveFor(InspectionId.of("does-not-exist")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("an unstarted inspection certifies nothing yet, so neither path is open")
    void anUnstartedInspectionCertifiesNothing() {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();

        assertThat(system.inspections.summaryOf(inspectionId).certificationPlan())
                .isEqualTo(new CertificationPlan.AsAWhole());
        assertThat(blockers(system.issueCertificate.issue(inspectionId)))
                .anyMatch(IssuanceBlocker.InspectionNotClosed.class::isInstance);
    }

    @Test
    @DisplayName("eligibility can be asked per subsystem, with the same rules issuance applies")
    void eligibilityCanBeAskedPerSubsystem() {
        InspectionId inspectionId = inspectAndClose("hazardous", "clean", true);

        assertThat(system.evaluateEligibility.blockersFor(inspectionId, ELECTRICAL))
                .anyMatch(IssuanceBlocker.UnverifiedRejection.class::isInstance);
        assertThat(system.evaluateEligibility.blockersFor(inspectionId, PRESSURE)).isEmpty();
        assertThatThrownBy(() -> system.evaluateEligibility
                .blockersFor(inspectionId, Subsystem.of("fire protection")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("does not certify subsystem");
    }

    @Test
    @DisplayName("each subsystem renews on its own, linked to the certificate it replaces")
    void aSubsystemRenewsOnItsOwn() {
        InspectionId first = inspectAndClose("clean", "clean", true);
        Certificate original = issuedCertificate(
                system.issueCertificate.issuePartial(first, ELECTRICAL));

        system.clock.advanceDays(400);
        InspectionId fresh = inspectAndClose("clean", "clean", true);
        Certificate renewed = issuedCertificate(
                system.renewCertificate.renewPartial(fresh, ELECTRICAL));

        assertThat(renewed.scope()).isEqualTo(CertificateScope.of(ELECTRICAL));
        assertThat(renewed.previousCertificateId()).contains(original.id());
        assertThat(renewed.backingInspectionId()).isEqualTo(fresh);
        assertThatThrownBy(() -> system.renewCertificate.renewPartial(fresh, PRESSURE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("has no certificate to renew");
    }

    @Test
    @DisplayName("closing the action of one subsystem reactivates only that certificate")
    void closingAnActionReactivatesItsSubsystem() {
        InspectionId inspectionId = inspectAndClose("untidy", "clean", true);
        Finding finding = system.findings.findByInspection(inspectionId).getFirst();
        system.planAsResponsible(finding.id(), "tidy the switchboard", PartyId.of("executor"),
                LocalDate.parse("2026-04-01"));
        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        Certificate pressure = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, PRESSURE));
        Instant originalExpiry = electrical.validity().expiresAt();

        system.clock.advanceDays(45);
        system.expireActions.sweep();
        assertThat(electrical.status()).isEqualTo(CertificateStatus.SUSPENDED);

        system.actingAs(PartyId.of("executor"), () -> system.reportExecution.report(finding.id(),
                "switchboard tidied", List.of("file://photo.jpg")));
        system.actingAs(inspector.id(), () -> system.verifyCorrectiveAction.verify(finding.id(),
                true, "inspected again"));

        assertThat(electrical.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(electrical.validity().expiresAt()).isEqualTo(originalExpiry);
        assertThat(pressure.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(system.deriveGlobalCertificate.deriveFor(inspectionId).derivedCertificate())
                .isPresent();
    }

    @Test
    @DisplayName("an event about nothing certifiable leaves every certificate alone")
    void anUnrelatedEventChangesNothing() {
        InspectionId inspectionId = inspectAndClose("clean", "clean", true);
        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));

        system.events.publish(() -> system.clock.now());

        assertThat(electrical.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(electrical.suspensions()).isEmpty();
    }

    @Test
    @DisplayName("a subsystem suspended for two reasons stays suspended until both clear")
    void twoCausesMustBothBeResolved() {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of("untidy"));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES,
                OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.no());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);

        List<Finding> findings = system.findings.findByInspection(inspectionId);
        assertThat(findings).hasSize(2);
        findings.forEach(finding -> system.planAsResponsible(finding.id(), "fix it",
                PartyId.of("executor"), LocalDate.parse("2026-04-01")));

        Certificate electrical = issuedCertificate(
                system.issueCertificate.issuePartial(inspectionId, ELECTRICAL));
        system.clock.advanceDays(45);
        system.expireActions.sweep();
        assertThat(electrical.unresolvedCauses()).hasSize(2);

        Finding first = findings.getFirst();
        system.actingAs(PartyId.of("executor"), () -> system.reportExecution.report(first.id(),
                "done", List.of("file://p.jpg")));
        system.actingAs(inspector.id(), () ->
                system.verifyCorrectiveAction.verify(first.id(), true, "checked"));

        assertThat(electrical.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(electrical.unresolvedCauses()).hasSize(1);

        Finding second = findings.get(1);
        system.actingAs(PartyId.of("executor"), () -> system.reportExecution.report(second.id(),
                "done", List.of("file://p.jpg")));
        system.actingAs(inspector.id(), () ->
                system.verifyCorrectiveAction.verify(second.id(), true, "checked"));

        assertThat(electrical.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(electrical.unresolvedCauses()).isEmpty();
    }

    @Test
    @DisplayName("a version that leaves a part of its asset types unevaluated is refused")
    void aVersionMustEvaluateEveryPartOfItsAssetTypes() {
        var schema = system.createSchema.create("Factory inspection", Set.of(AssetType.FACTORY));
        system.openDraft.open(schema.id());
        system.editDraft.addSection(schema.id(), Section.of("Electrical", 1,
                DomainWorld.electricalCriterion()));

        var refused = system.publishSchemaVersion.publish(schema.id());

        assertThat(refused.published()).isFalse();
        assertThat(refused.violations())
                .anyMatch(violation -> violation.contains("pressure system")
                        && violation.contains("no criterion of this version evaluates"));

        system.editDraft.addSection(schema.id(), Section.of("Pressure", 2,
                DomainWorld.pressureCriterion()));
        assertThat(system.publishSchemaVersion.publish(schema.id()).published()).isTrue();
    }

    @Test
    @DisplayName("the global certificate answers for every part the asset has, not only the evaluated ones")
    void theGlobalCertificateCoversEveryPartTheAssetHas() {
        Asset threeParts = system.registerAsset.register("Annex", AssetType.FACILITY,
                responsible.id(), "Building 4", Map.of("room", "4", "purpose", "storage"),
                Set.of(ELECTRICAL, PRESSURE, DomainWorld.BUILDING_SAFETY), JurisdictionId.of("REFERENCE"));
        InspectionId inspectionId = system.assignInspection
                .assign(threeParts.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.BUILDING_EXITS, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.closeInspection.close(inspectionId);

        system.issueCertificate.issuePartial(inspectionId, ELECTRICAL);
        system.issueCertificate.issuePartial(inspectionId, PRESSURE);

        var incomplete = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(incomplete.derivedCertificate()).isEmpty();
        assertThat(incomplete.describe()).contains("building safety has no certificate");

        system.issueCertificate.issuePartial(inspectionId, DomainWorld.BUILDING_SAFETY);

        var complete = system.deriveGlobalCertificate.deriveFor(inspectionId);
        assertThat(complete.derivedCertificate().orElseThrow().coveredSubsystems())
                .containsExactly(ELECTRICAL, PRESSURE, DomainWorld.BUILDING_SAFETY);
    }

    @Test
    @DisplayName("applicability cannot hand a type to a version that evaluates none of its parts")
    void applicabilityCannotBypassTheCoverageRule() {
        var generic = system.createSchema.create("Generic", Set.of(AssetType.EQUIPMENT));
        system.openDraft.open(generic.id());
        system.editDraft.addSection(generic.id(), Section.of("Generic", 1,
                DomainWorld.temperatureCriterion()));
        assertThat(system.publishSchemaVersion.publish(generic.id()).published()).isTrue();

        var change = new ChangeSchemaApplicability(system.schemas, system.schemaApplicability,
                system.audit);

        assertThatThrownBy(() -> change.applyTo(generic.id(), AssetType.FACTORY))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("evaluates no criterion for")
                .hasMessageContaining("assets of type FACTORY may have");

        assertThatThrownBy(() -> change.transferTo(generic.id(), generic.id(), AssetType.EQUIPMENT))
                .isInstanceOf(DomainException.class);
        assertThat(system.schemas.require(generic.id()).applicableAssetTypes())
                .containsExactly(AssetType.EQUIPMENT);
    }

    private InspectionId inspectAndClose(String electrical, String pressure, boolean documentation) {
        InspectionId inspectionId = system.assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, DomainWorld.ELECTRICAL_WIRING,
                OptionAnswer.of(electrical));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES,
                OptionAnswer.of(pressure));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        if (documentation) {
            system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION,
                    DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        }
        system.closeInspection.close(inspectionId);
        return inspectionId;
    }
}
