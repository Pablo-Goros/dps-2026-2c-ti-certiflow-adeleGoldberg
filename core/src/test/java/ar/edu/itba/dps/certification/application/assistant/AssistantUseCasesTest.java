package ar.edu.itba.dps.certification.application.assistant;

import ar.edu.itba.dps.certification.application.assistant.usecase.DecideSuggestion;
import ar.edu.itba.dps.certification.application.assistant.usecase.RequestAssistance;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionDecision;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import ar.edu.itba.dps.certification.support.DirectTransactions;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;
import ar.edu.itba.dps.certification.support.InMemoryConsultations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantUseCasesTest {

    private static final CriterionId ELEC = DomainWorld.ELECTRICAL_WIRING;
    private static final SuggestionId FIRST = SuggestionId.of("s1");

    private FullSystem system;
    private Party inspector;
    private InspectionId inspectionId;
    private InMemoryConsultations consultations;
    private RequestAssistance request;
    private DecideSuggestion decide;
    private Supplier<AssistantReply> answer;
    private AssistanceRequest seen;
    private int calls;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        Asset asset = system.registerAsset.register("Central Facility", AssetType.FACILITY, responsible.id(),
                "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("REFERENCE"));
        inspectionId = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(inspectionId);
        system.recordAnswer.record(inspectionId, ELEC, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.PRESSURE_VALVES, OptionAnswer.of("clean"));
        system.recordAnswer.record(inspectionId, DomainWorld.DOCUMENTATION, YesNoAnswer.yes());
        system.attachEvidence.attach(inspectionId, DomainWorld.DOCUMENTATION, DomainWorld.SAFETY_MANUAL, "file://manual.pdf");
        system.recordNote.record(inspectionId, Optional.empty(), "cable con la aislación pelada junto al tablero");

        answer = () -> new AssistantReply(new AssistantVersion("test", "fixed", "v1"), List.of(new SuggestedFinding(
                ELEC, CriterionResult.REJECTED, Severity.CRITICAL, "exposed conductor next to the panel",
                "the general note describes a stripped cable")));
        consultations = new InMemoryConsultations();
        var recordObservation = new RecordObservedNonConformity(system.inspections, system.ids, system.clock,
                system.audit, system.actors, DirectTransactions.INSTANCE);
        request = new RequestAssistance(system.inspections, system.schemaCatalog, given -> {
            seen = given;
            calls++;
            return answer.get();
        }, consultations, system.ids, system.clock, system.audit, system.actors, DirectTransactions.INSTANCE);
        decide = new DecideSuggestion(consultations, system.inspections, recordObservation, system.clock, system.audit,
                system.actors, DirectTransactions.INSTANCE);
    }

    @Test
    @DisplayName("requesting assistance records the consultation and changes nothing in the inspection")
    void requestingChangesNothingButTheConsultation() {
        Consultation consultation = request.request(inspectionId);

        assertThat(consultations.findById(consultation.id())).contains(consultation);
        assertThat(seen).isEqualTo(consultation.request());
        assertThat(consultation.suggestions()).hasSize(1);
        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();
        assertThat(inspection().status()).isEqualTo(InspectionStatus.IN_PROGRESS);
        assertThat(system.findings.findByInspection(inspectionId)).isEmpty();
        assertThat(audited(AuditAction.ASSISTANCE_REQUESTED)).singleElement().satisfies(entry -> {
            assertThat(entry.actor().displayName()).isEqualTo(inspector.name());
            assertThat(entry.detail().toString()).contains("consultation " + consultation.id());
        });
    }

    @Test
    @DisplayName("a consultation is dated when it was asked, not when the model answered")
    void aConsultationIsDatedWhenItWasAsked() {
        Instant asked = system.clock.now();
        answer = () -> {
            system.clock.advance(Duration.ofSeconds(45));
            return new AssistantReply(new AssistantVersion("test", "fixed", "v1"), List.of());
        };

        assertThat(request.request(inspectionId).requestedAt()).isEqualTo(asked);
    }

    @Test
    @DisplayName("an unavailable assistant leaves nothing recorded")
    void anUnavailableAssistantRecordsNothing() {
        answer = () -> {
            throw new AssistanceUnavailableException(AssistanceUnavailableException.Reason.UNREACHABLE, "timed out");
        };

        assertThatThrownBy(() -> request.request(inspectionId))
                .isInstanceOfSatisfying(AssistanceUnavailableException.class, failure ->
                        assertThat(failure.reason()).isEqualTo(AssistanceUnavailableException.Reason.UNREACHABLE));
        assertThat(consultations.findByInspection(inspectionId)).isEmpty();
        assertThat(audited(AuditAction.ASSISTANCE_REQUESTED)).isEmpty();
    }

    @Test
    @DisplayName("a stranger or a closed inspection is refused before the assistant is called")
    void refusalsCostNoCall() {
        assertThatThrownBy(() -> system.actingAs(PartyId.of("intruder"), () -> request.request(inspectionId)))
                .hasMessageContaining("only the assigned inspector");

        system.closeInspection.close(inspectionId);

        assertThatThrownBy(() -> request.request(inspectionId)).hasMessageContaining("while it is CLOSED");
        assertThat(calls).isZero();
    }

    @Test
    @DisplayName("accepting records the suggestion as the inspector's observed non-conformity, and closing rejects the criterion")
    void acceptingRecordsTheObservation() {
        Consultation consultation = request.request(inspectionId);

        decide.accept(inspectionId, consultation.id(), FIRST);

        var observation = inspection().requireRecord(ELEC).observations().getFirst();
        assertThat(observation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(observation.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(observation.description()).isEqualTo("exposed conductor next to the panel");
        assertThat(observation.recordedBy()).isEqualTo(inspector.id());
        assertThat(consultations.require(consultation.id()).requireSuggestion(FIRST).decision()).get()
                .isInstanceOfSatisfying(SuggestionDecision.Accepted.class, decision ->
                        assertThat(decision.observationId()).isEqualTo(observation.id()));
        assertThat(audited(AuditAction.INSPECTION_DATA_RECORDED)).anySatisfy(entry ->
                assertThat(entry.detail().toString()).contains("observation." + ELEC + "." + observation.id()));
        assertThat(audited(AuditAction.ASSISTANCE_SUGGESTION_DECIDED)).singleElement()
                .satisfies(entry -> assertThat(entry.detail().toString()).contains("accepted"));

        system.closeInspection.close(inspectionId);

        var evaluation = inspection().requireRecord(ELEC).currentEvaluation().orElseThrow();
        assertThat(evaluation.result()).isEqualTo(CriterionResult.REJECTED);
        assertThat(evaluation.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("editing records the inspector's version, and a refused edit records nothing")
    void editingRecordsTheInspectorsVersion() {
        Consultation consultation = request.request(inspectionId);

        assertThatThrownBy(() -> decide.edit(inspectionId, consultation.id(), FIRST, ELEC, CriterionResult.OBSERVED,
                Severity.HIGH, "worn insulation", " ")).hasMessageContaining("edit note");
        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();

        decide.edit(inspectionId, consultation.id(), FIRST, ELEC, CriterionResult.OBSERVED, Severity.HIGH,
                "worn insulation", "the conductor is worn, not exposed");

        var observation = inspection().requireRecord(ELEC).observations().getFirst();
        assertThat(observation.result()).isEqualTo(CriterionResult.OBSERVED);
        assertThat(observation.severity()).isEqualTo(Severity.HIGH);
        assertThat(consultations.require(consultation.id()).requireSuggestion(FIRST).decision()).get()
                .isInstanceOfSatisfying(SuggestionDecision.Edited.class, decision -> {
                    assertThat(decision.observationId()).isEqualTo(observation.id());
                    assertThat(decision.note()).isEqualTo("the conductor is worn, not exposed");
                });
    }

    @Test
    @DisplayName("discarding records only the decision, and a suggestion is decided once")
    void discardingRecordsOnlyTheDecision() {
        Consultation consultation = request.request(inspectionId);

        decide.discard(inspectionId, consultation.id(), FIRST, Optional.of("the cable belongs to the neighbouring building"));

        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();
        assertThat(consultations.require(consultation.id()).requireSuggestion(FIRST).decision()).get()
                .isInstanceOf(SuggestionDecision.Discarded.class);
        assertThatThrownBy(() -> decide.accept(inspectionId, consultation.id(), FIRST))
                .hasMessageContaining("already decided");
        assertThat(inspection().requireRecord(ELEC).observations()).isEmpty();
    }

    private Inspection inspection() {
        return system.inspections.require(inspectionId);
    }

    private List<ar.edu.itba.dps.certification.domain.audit.AuditEntry> audited(AuditAction action) {
        return system.auditTrail.entriesFor(AuditedElementRef.inspection(inspectionId.value()), action);
    }
}
