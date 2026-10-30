package ar.edu.itba.dps.certification;

import ar.edu.itba.dps.certification.application.assistant.usecase.DecideSuggestion;
import ar.edu.itba.dps.certification.application.assistant.usecase.RequestAssistance;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DirectTransactions;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import ar.edu.itba.dps.certification.support.InMemoryConsultations;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ar.edu.itba.dps.certification.support.Decisions.blockers;
import static ar.edu.itba.dps.certification.support.Decisions.issuedCertificate;
import static org.assertj.core.api.Assertions.assertThat;

/** The assistant from the consultation to the certificate, and what it never does on its own. */
class AssistedInspectionIT {

    private static final CriterionId ELEC = DomainWorld.ELECTRICAL_WIRING;
    private static final SuggestionId CABLE = SuggestionId.of("s1");
    private static final SuggestionId VALVE = SuggestionId.of("s2");

    private FullSystem system;
    private Party inspector;
    private Asset asset;
    private InMemoryConsultations consultations;
    private RequestAssistance requestAssistance;
    private DecideSuggestion decide;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        asset = system.registerAsset.register("Central Facility", AssetType.FACILITY, responsible.id(), "Building 1",
                Map.of("room", "12", "purpose", "research"), Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE),
                JurisdictionId.of("REFERENCE"));
        consultations = new InMemoryConsultations();
        requestAssistance = new RequestAssistance(system.inspections, system.schemaCatalog,
                request -> new AssistantReply(new AssistantVersion("test", "fixed", "v1"), List.of(
                        new SuggestedFinding(ELEC, CriterionResult.REJECTED, Severity.CRITICAL,
                                "exposed conductor next to the panel", "the general note describes a stripped cable"),
                        new SuggestedFinding(DomainWorld.PRESSURE_VALVES, CriterionResult.OBSERVED, Severity.LOW,
                                "valve label faded", "the note mentions the label"))),
                consultations, system.ids, system.clock, system.audit, system.actors, DirectTransactions.INSTANCE);
        decide = new DecideSuggestion(consultations, system.inspections, new RecordObservedNonConformity(
                system.inspections, system.ids, system.clock, system.audit, system.actors, DirectTransactions.INSTANCE),
                system.clock, system.audit, system.actors, DirectTransactions.INSTANCE);
    }

    @Test
    @DisplayName("an accepted suggestion about the stripped cable blocks only the electrical certificate, with the reading intact")
    void anAcceptedSuggestionBlocksThePartItConcerns() {
        InspectionId id = inspectWithANoteAboutTheCable();
        Consultation consultation = requestAssistance.request(id);

        decide.accept(id, consultation.id(), CABLE);
        decide.discard(id, consultation.id(), VALVE, Optional.of("the label is legible"));
        system.closeInspection.close(id);

        var record = system.inspections.require(id).requireRecord(ELEC);
        assertThat(record.answer()).contains(OptionAnswer.of("clean"));
        assertThat(record.currentEvaluation().orElseThrow().result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(system.findings.findByInspection(id)).singleElement().satisfies(finding -> {
            assertThat(finding.criterionId()).isEqualTo(ELEC);
            assertThat(finding.severity()).isEqualTo(Severity.CRITICAL);
        });
        assertThat(blockers(system.issueCertificate.issuePartial(id, DomainWorld.ELECTRICAL))).isNotEmpty();
        assertThat(issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.PRESSURE)).scope())
                .isEqualTo(CertificateScope.of(DomainWorld.PRESSURE));
    }

    @Test
    @DisplayName("consulting and deciding never close the inspection, record findings or issue certificates")
    void theAssistantNeverActsOnItsOwn() {
        InspectionId id = inspectWithANoteAboutTheCable();
        Consultation first = requestAssistance.request(id);
        decide.edit(id, first.id(), CABLE, ELEC, CriterionResult.OBSERVED, Severity.MEDIUM, "worn insulation",
                "the insulation is worn, not cut");
        decide.discard(id, first.id(), VALVE, Optional.empty());
        requestAssistance.request(id);

        assertThat(system.inspections.require(id).status()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(system.findings.findByInspection(id)).isEmpty();
        assertThat(system.certificates.findAll()).isEmpty();
        assertThat(consultations.findByInspection(id)).hasSize(2);
    }

    @Test
    @DisplayName("with every suggestion discarded the criteria close as the rules evaluated them")
    void discardedSuggestionsLeaveTheRuleResult() {
        InspectionId id = inspectWithANoteAboutTheCable();
        Consultation consultation = requestAssistance.request(id);

        decide.discard(id, consultation.id(), CABLE, Optional.of("the cable belongs to the neighbouring building"));
        decide.discard(id, consultation.id(), VALVE, Optional.empty());
        system.closeInspection.close(id);

        assertThat(system.inspections.require(id).requireRecord(ELEC).currentEvaluation().orElseThrow().result())
                .isEqualTo(CriterionResult.APPROVED);
        assertThat(system.findings.findByInspection(id)).isEmpty();
        assertThat(issuedCertificate(system.issueCertificate.issuePartial(id, DomainWorld.ELECTRICAL)).scope())
                .isEqualTo(CertificateScope.of(DomainWorld.ELECTRICAL));
    }

    private InspectionId inspectWithANoteAboutTheCable() {
        InspectionId id = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, ELEC, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(id, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(id, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.recordNote.record(id, Optional.empty(), "cable con la aislación pelada junto al tablero");
        return id;
    }
}
