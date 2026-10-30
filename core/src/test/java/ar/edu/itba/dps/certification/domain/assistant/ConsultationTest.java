package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FullSystem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Calls the aggregate directly: what it refuses, it refuses whoever the caller is. */
class ConsultationTest {

    private static final CriterionId ELEC = DomainWorld.ELECTRICAL_WIRING;
    private static final CriterionId PRES = DomainWorld.PRESSURE_VALVES;
    private static final CriterionId SAFE = DomainWorld.BUILDING_EXITS;
    private static final AssistantVersion VERSION = new AssistantVersion("test", "fixed", "v1");
    private static final SuggestionId FIRST = SuggestionId.of("s1");

    private FullSystem system;
    private Party inspector;
    private Party responsible;
    private SchemaVersion frozen;
    private InspectionId inspectionId;
    private AssistanceRequest request;

    @BeforeEach
    void setUp() {
        system = new FullSystem();
        frozen = system.publishSubsystemSchema(AssetType.FACILITY);
        inspector = system.person("Ana Perez");
        system.actAs(inspector);
        responsible = system.organization("Favaloro Foundation");
        inspectionId = startedInspection("Central Facility");
        system.recordAnswer.record(inspectionId, ELEC, OptionAnswer.of("clean"));
        system.recordNote.record(inspectionId, Optional.empty(), "cable con la aislación pelada junto al tablero");
        request = AssistanceRequests.of(inspection(), frozen, now());
    }

    @Test
    @DisplayName("valid suggestions are numbered and invalid ones are kept with the reason they were refused")
    void theReplyIsClassified() {
        Consultation consultation = record(request,
                finding(ELEC, CriterionResult.REJECTED, Severity.CRITICAL, "exposed conductor", "the note describes it"),
                finding(SAFE, CriterionResult.REJECTED, Severity.HIGH, "blocked exit", "seen"),
                finding(PRES, CriterionResult.APPROVED, Severity.LOW, "fine", "seen"),
                finding(PRES, CriterionResult.OBSERVED, Severity.LOW, "leak", " "),
                finding(ELEC, CriterionResult.OBSERVED, Severity.LOW, "again", "again"));

        assertThat(consultation.suggestions()).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.id()).isEqualTo(FIRST);
            assertThat(suggestion.criterionId()).isEqualTo(ELEC);
            assertThat(suggestion.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(suggestion.decided()).isFalse();
        });
        assertThat(consultation.rejected()).extracting(RejectedSuggestion::reason).containsExactly(
                "criterion SAFE was not part of the consultation",
                "an approving result is not a finding",
                "a suggestion needs a description and a rationale",
                "criterion ELEC was already suggested in this consultation");
        assertThat(consultation.version()).isEqualTo(VERSION);
        assertThat(consultation.request()).isEqualTo(request);
    }

    @Test
    @DisplayName("only the assigned inspector consults about an inspection in progress, about criteria it applies")
    void recordingIsGuarded() {
        assertThatThrownBy(() -> Consultation.record(ConsultationId.of("c-1"), inspection(), PartyId.of("intruder"),
                now(), request, reply())).hasMessageContaining("only the assigned inspector");
        AssistanceRequest forged = new AssistanceRequest(request.assetType(), request.subsystems(),
                List.of(new CriterionContext(SAFE, "Safety", Optional.of(DomainWorld.BUILDING_SAFETY), Optional.empty(),
                        Optional.empty(), List.of(), List.of(), request.criteria().getFirst().outcomes())),
                request.observations());
        assertThatThrownBy(() -> record(forged)).hasMessageContaining("is not applicable");

        system.closeInspection.close(inspectionId);

        assertThatThrownBy(() -> Consultation.ensureConsultable(inspection(), inspector.id()))
                .hasMessageContaining("while it is CLOSED");
        assertThatThrownBy(() -> record(request)).hasMessageContaining("while it is CLOSED");
    }

    @Test
    @DisplayName("accepting names the observed non-conformity the inspector recorded with exactly the suggested values")
    void acceptingNeedsTheMatchingObservation() {
        Consultation consultation = record(request,
                finding(ELEC, CriterionResult.REJECTED, Severity.CRITICAL, "exposed conductor", "the note describes it"));
        inspection().recordObservedNonConformity(inspector.id(), ELEC, "o-1", CriterionResult.REJECTED, Severity.HIGH,
                "exposed conductor", Optional.empty(), now());

        assertThatThrownBy(() -> consultation.accept(FIRST, "o-1", inspection(), inspector.id(), now()))
                .hasMessageContaining("does not record what was decided");

        inspection().recordObservedNonConformity(inspector.id(), ELEC, "o-2", CriterionResult.REJECTED,
                Severity.CRITICAL, "exposed conductor", Optional.empty(), now());
        Suggestion accepted = consultation.accept(FIRST, "o-2", inspection(), inspector.id(), now());

        assertThat(accepted.decision()).get().isInstanceOfSatisfying(SuggestionDecision.Accepted.class, decision -> {
            assertThat(decision.observationId()).isEqualTo("o-2");
            assertThat(decision.by()).isEqualTo(inspector.id());
        });
        assertThatThrownBy(() -> consultation.discard(FIRST, Optional.empty(), inspection(), inspector.id(), now()))
                .hasMessageContaining("already decided");
    }

    @Test
    @DisplayName("a decision is refused for strangers, unknown suggestions, invalid edits, other inspections and closed ones")
    void decisionsAreGuarded() {
        Consultation consultation = record(request,
                finding(ELEC, CriterionResult.REJECTED, Severity.CRITICAL, "exposed conductor", "the note describes it"));

        assertThatThrownBy(() -> consultation.discard(FIRST, Optional.empty(), inspection(), PartyId.of("intruder"), now()))
                .hasMessageContaining("only the assigned inspector");
        assertThatThrownBy(() -> consultation.discard(SuggestionId.of("s9"), Optional.empty(), inspection(),
                inspector.id(), now())).hasMessageContaining("does not belong to consultation");
        assertThatThrownBy(() -> consultation.requireEditable(FIRST, CriterionResult.APPROVED, "fine after all",
                inspection(), inspector.id())).hasMessageContaining("discard it instead");
        assertThatThrownBy(() -> consultation.requireEditable(FIRST, CriterionResult.OBSERVED, " ",
                inspection(), inspector.id())).hasMessageContaining("edit note");
        assertThatThrownBy(() -> consultation.discard(FIRST, Optional.of(" "), inspection(), inspector.id(), now()))
                .hasMessageContaining("discard reason");
        Inspection other = system.inspections.require(startedInspection("Annex"));
        assertThatThrownBy(() -> consultation.discard(FIRST, Optional.empty(), other, inspector.id(), now()))
                .hasMessageContaining("belongs to inspection");

        system.closeInspection.close(inspectionId);

        assertThatThrownBy(() -> consultation.discard(FIRST, Optional.empty(), inspection(), inspector.id(), now()))
                .hasMessageContaining("while it is CLOSED");
        assertThat(consultation.requireSuggestion(FIRST).decided()).isFalse();
    }

    private InspectionId startedInspection(String assetName) {
        Asset asset = system.registerAsset.register(assetName, AssetType.FACILITY, responsible.id(), "Building 1",
                Map.of("room", "12", "purpose", "research"), Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE),
                JurisdictionId.of("REFERENCE"));
        InspectionId id = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        return id;
    }

    private Consultation record(AssistanceRequest input, SuggestedFinding... findings) {
        return Consultation.record(ConsultationId.of("c-1"), inspection(), inspector.id(), now(), input,
                new AssistantReply(VERSION, List.of(findings)));
    }

    private AssistantReply reply() {
        return new AssistantReply(VERSION, List.of());
    }

    private static SuggestedFinding finding(CriterionId criterion, CriterionResult result, Severity severity,
            String description, String rationale) {
        return new SuggestedFinding(criterion, result, severity, description, rationale);
    }

    private Inspection inspection() {
        return system.inspections.require(inspectionId);
    }

    private Instant now() {
        return system.clock.now();
    }
}
