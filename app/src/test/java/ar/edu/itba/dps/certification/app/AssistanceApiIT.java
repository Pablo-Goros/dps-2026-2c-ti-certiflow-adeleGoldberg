package ar.edu.itba.dps.certification.app;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;
import ar.edu.itba.dps.certification.domain.assistant.port.inbound.BrowseConsultations;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assistant over HTTP with a scripted model. Storing and browsing consultations are stand-ins
 * here until the persistence work provides them; everything else is the real application over H2.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:certiflow-assistance-it;DB_CLOSE_DELAY=-1",
            "certiflow.assistant.enabled=true"
        })
class AssistanceApiIT extends ApiTest {

    private static final AtomicInteger calls = new AtomicInteger();
    private static volatile Function<AssistanceRequest, AssistantReply> model;

    @TestConfiguration(proxyBeanMethods = false)
    static class StandIns {

        @Bean
        ConsultationRepository consultations() {
            return new MemoryConsultations();
        }

        @Bean
        BrowseConsultations browseConsultations(ConsultationRepository consultations) {
            return new BrowseConsultations() {
                @Override
                public List<Consultation> of(InspectionId inspectionId) {
                    return consultations.findByInspection(inspectionId).stream()
                            .sorted(Comparator.comparing(Consultation::requestedAt).reversed())
                            .toList();
                }

                @Override
                public Optional<Consultation> find(ConsultationId consultationId) {
                    return consultations.findById(consultationId);
                }
            };
        }

        @Bean
        @Primary
        InspectionAssistant scriptedModel() {
            return request -> {
                calls.incrementAndGet();
                return model.apply(request);
            };
        }
    }

    static final class MemoryConsultations implements ConsultationRepository {

        private final Map<ConsultationId, Consultation> stored = new ConcurrentHashMap<>();

        @Override
        public void save(Consultation consultation) {
            stored.put(consultation.id(), consultation);
        }

        @Override
        public Optional<Consultation> findById(ConsultationId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Consultation> findByInspection(InspectionId inspectionId) {
            return stored.values().stream().filter(consultation -> consultation.inspectionId().equals(inspectionId)).toList();
        }
    }

    private String inspector;
    private String inspectionId;

    @BeforeEach
    void startAFacilityInspection() {
        calls.set(0);
        model = request -> reply(
                finding("ELEC", CriterionResult.REJECTED, Severity.CRITICAL, "conductor expuesto junto al tablero"),
                finding("PRES", CriterionResult.OBSERVED, Severity.LOW, "etiqueta de la válvula borrosa"),
                finding("SAFE", CriterionResult.REJECTED, Severity.HIGH, "salida bloqueada"));
        publishFacilitySchemaOnce();
        String owner = organization("Owner");
        inspector = person("Inspector");
        var asset = new HashMap<String, Object>();
        asset.put("name", "Plant " + System.nanoTime());
        asset.put("assetType", "FACILITY");
        asset.put("responsibleId", owner);
        asset.put("location", "Building 1");
        asset.put("characteristics", Map.of("room", "1"));
        asset.put("jurisdiction", "REFERENCE");
        asset.put("subsystems", List.of("electrical installation", "pressure system"));
        String assetId = api.post("/api/assets", asset, null).text("id");
        inspectionId = api.post("/api/inspections", Map.of("assetId", assetId, "inspectorId", inspector,
                "expectedDate", "2027-01-15"), null).text("id");
        assertThat(api.post(inspection("/start"), Map.of(), inspector).status()).isEqualTo(200);
        api.put(inspection("/answers/ELEC"), Fixtures.option("clean"), inspector);
        api.put(inspection("/answers/PRES"), Fixtures.option("clean"), inspector);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAcceptedSuggestionRejectsTheCriterionWhoseReadingTheRuleApprovesAndBlocksItsCertificate() {
        api.post(inspection("/notes"), Map.of("text", "cable con la aislación pelada junto al tablero"), inspector);

        var consulted = api.post(consultations(), Map.of(), inspector);
        String consultationId = consulted.text("id");
        var accepted = api.post(decision(consultationId, "s1"), Map.of("verdict", "ACCEPTED"), inspector);
        var observations = (List<Map<String, Object>>) criterion(api.get(inspection("")), "ELEC").get("observedNonConformities");
        api.post(decision(consultationId, "s2"), Map.of("verdict", "DISCARDED"), inspector);
        var closed = api.post(inspection("/close"), Map.of(), inspector);
        var electrical = api.post(inspection("/certificates"), Map.of("subsystem", "electrical installation"), null);
        var pressure = api.post(inspection("/certificates"), Map.of("subsystem", "pressure system"), null);

        assertThat(consulted.status()).as(consulted.body()).isEqualTo(201);
        assertThat(consulted.text("requestedBy")).isEqualTo(inspector);
        assertThat((Map<String, Object>) consulted.json().get("assistant"))
                .containsEntry("adapter", "test").containsEntry("model", "scripted").containsEntry("prompt", "v1");
        assertThat(suggestions(consulted)).first().satisfies(suggestion -> assertThat(suggestion)
                .containsEntry("id", "s1").containsEntry("criterionId", "ELEC").containsEntry("section", "Subsystems")
                .containsEntry("result", "REJECTED").containsEntry("severity", "CRITICAL").containsEntry("decision", null));
        assertThat((List<Map<String, Object>>) consulted.json().get("rejected")).singleElement().satisfies(rejected ->
                assertThat(rejected).containsEntry("criterionId", "SAFE")
                        .containsEntry("reason", "criterion SAFE was not part of the consultation"));
        assertThat(accepted.status()).as(accepted.body()).isEqualTo(200);
        var decision = (Map<String, Object>) suggestions(accepted).getFirst().get("decision");
        assertThat(decision).containsEntry("verdict", "ACCEPTED").containsEntry("by", inspector);
        assertThat(observations).singleElement().satisfies(observation -> assertThat(observation)
                .containsEntry("id", decision.get("observationId")).containsEntry("severity", "CRITICAL")
                .containsEntry("description", "conductor expuesto junto al tablero"));
        assertThat(closed.status()).as(closed.body()).isEqualTo(200);
        var evaluation = (Map<String, Object>) criterion(closed, "ELEC").get("evaluation");
        assertThat(evaluation).containsEntry("result", "REJECTED").containsEntry("severity", "CRITICAL");
        assertThat(((Map<String, Object>) criterion(closed, "ELEC").get("answer")).get("option")).isEqualTo("clean");
        assertThat(electrical.status()).as(electrical.body()).isEqualTo(422);
        assertThat(electrical.text("outcome")).isEqualTo("BLOCKED");
        assertThat(pressure.status()).as(pressure.body()).isEqualTo(201);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEditedSuggestionRecordsTheInspectorsVersionAndADiscardedOneNothing() {
        api.post(inspection("/notes"), Map.of("text", "aislación gastada en el tablero"), inspector);
        String consultationId = api.post(consultations(), Map.of(), inspector).text("id");

        var edited = api.post(decision(consultationId, "s1"), Map.of("verdict", "EDITED", "criterionId", "ELEC",
                "result", "OBSERVED", "severity", "MEDIUM", "description", "aislación gastada",
                "note", "la aislación está gastada, no cortada"), inspector);
        var discarded = api.post(decision(consultationId, "s2"), Map.of("verdict", "DISCARDED",
                "reason", "la etiqueta se lee bien"), inspector);
        var listed = api.get(consultations());

        assertThat(edited.status()).as(edited.body()).isEqualTo(200);
        assertThat((Map<String, Object>) suggestions(edited).getFirst().get("decision"))
                .containsEntry("verdict", "EDITED").containsEntry("result", "OBSERVED")
                .containsEntry("note", "la aislación está gastada, no cortada");
        assertThat(discarded.status()).as(discarded.body()).isEqualTo(200);
        assertThat((Map<String, Object>) suggestions(discarded).get(1).get("decision"))
                .containsEntry("verdict", "DISCARDED").containsEntry("reason", "la etiqueta se lee bien");
        var line = criterion(api.get(inspection("")), "ELEC");
        assertThat((List<Map<String, Object>>) line.get("observedNonConformities")).singleElement()
                .satisfies(observation -> assertThat(observation).containsEntry("result", "OBSERVED")
                        .containsEntry("severity", "MEDIUM").containsEntry("description", "aislación gastada"));
        assertThat((List<?>) criterion(api.get(inspection("")), "PRES").get("observedNonConformities")).isEmpty();
        assertThat(listed.jsonList()).singleElement().satisfies(consultation ->
                assertThat(consultation).containsEntry("id", consultationId));
    }

    @Test
    void theApiRefusesWhatTheDomainRefusesAndARefusalCostsNoCall() {
        var withoutNotes = api.post(consultations(), Map.of(), inspector);
        api.post(inspection("/notes"), Map.of("text", "cable pelado"), inspector);
        var anonymous = api.post(consultations(), Map.of(), null);
        var stranger = api.post(consultations(), Map.of(), person("Stranger"));
        var unknownInspection = api.post("/api/inspections/missing/consultations", Map.of(), inspector);
        String consultationId = api.post(consultations(), Map.of(), inspector).text("id");
        var withoutVerdict = api.post(decision(consultationId, "s1"), Map.of(), inspector);
        var unknownVerdict = api.post(decision(consultationId, "s1"), Map.of("verdict", "MAYBE"), inspector);
        var approvingEdit = api.post(decision(consultationId, "s1"), Map.of("verdict", "EDITED", "criterionId", "ELEC",
                "result", "APPROVED", "severity", "LOW", "description", "todo bien", "note", "revisado"), inspector);
        var unknownSuggestion = api.post(decision(consultationId, "s9"), Map.of("verdict", "ACCEPTED"), inspector);
        var unknownConsultation = api.post(decision("missing", "s1"), Map.of("verdict", "ACCEPTED"), inspector);
        api.post(decision(consultationId, "s1"), Map.of("verdict", "ACCEPTED"), inspector);
        var decidedTwice = api.post(decision(consultationId, "s1"), Map.of("verdict", "DISCARDED"), inspector);

        assertThat(withoutNotes.status()).as(withoutNotes.body()).isEqualTo(422);
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(stranger.status()).isEqualTo(422);
        assertThat(unknownInspection.status()).isEqualTo(404);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(withoutVerdict.status()).isEqualTo(400);
        assertThat(unknownVerdict.status()).isEqualTo(400);
        assertThat(approvingEdit.status()).as(approvingEdit.body()).isEqualTo(422);
        assertThat(unknownSuggestion.status()).isEqualTo(422);
        assertThat(unknownConsultation.status()).isEqualTo(404);
        assertThat(decidedTwice.status()).isEqualTo(422);
    }

    @Test
    void anUnavailableModelIsA503ThatRecordsNothing() {
        api.post(inspection("/notes"), Map.of("text", "cable pelado"), inspector);
        model = request -> {
            throw new AssistanceUnavailableException(AssistanceUnavailableException.Reason.INCOMPLETE, "cut");
        };

        var failed = api.post(consultations(), Map.of(), inspector);

        assertThat(failed.status()).isEqualTo(503);
        assertThat(failed.text("code")).isEqualTo("ASSISTANT_UNAVAILABLE");
        assertThat(failed.json().get("details")).isEqualTo(List.of("INCOMPLETE"));
        assertThat(api.get(consultations()).jsonList()).isEmpty();
        assertThat(api.get("/api/audit?type=INSPECTION&id=" + inspectionId).body()).doesNotContain("ASSISTANCE_REQUESTED");
    }

    @Test
    void consultationsAndDecisionsAreAuditedOnTheInspection() {
        api.post(inspection("/notes"), Map.of("text", "cable pelado"), inspector);
        String consultationId = api.post(consultations(), Map.of(), inspector).text("id");
        api.post(decision(consultationId, "s1"), Map.of("verdict", "ACCEPTED"), inspector);

        String audit = api.get("/api/audit?type=INSPECTION&id=" + inspectionId).body();

        assertThat(audit).contains("ASSISTANCE_REQUESTED", "ASSISTANCE_SUGGESTION_DECIDED", "observation.ELEC.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyDiscardReasonCountsAsNone() {
        api.post(inspection("/notes"), Map.of("text", "cable pelado"), inspector);
        String consultationId = api.post(consultations(), Map.of(), inspector).text("id");

        var discarded = api.post(decision(consultationId, "s1"), Map.of("verdict", "DISCARDED", "reason", ""), inspector);

        assertThat(discarded.status()).as(discarded.body()).isEqualTo(200);
        assertThat((Map<String, Object>) suggestions(discarded).getFirst().get("decision"))
                .containsEntry("verdict", "DISCARDED").containsEntry("reason", null);
    }

    private String inspection(String suffix) {
        return "/api/inspections/" + inspectionId + suffix;
    }

    private String consultations() {
        return inspection("/consultations");
    }

    private String decision(String consultationId, String suggestionId) {
        return consultations() + "/" + consultationId + "/suggestions/" + suggestionId + "/decision";
    }

    private static AssistantReply reply(SuggestedFinding... findings) {
        return new AssistantReply(new AssistantVersion("test", "scripted", "v1"), List.of(findings));
    }

    private static SuggestedFinding finding(String criterionId, CriterionResult result, Severity severity, String description) {
        return new SuggestedFinding(CriterionId.of(criterionId), result, severity, description, "la nota lo describe");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> suggestions(Api.Reply consultation) {
        return (List<Map<String, Object>>) consultation.json().get("suggestions");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> criterion(Api.Reply inspection, String id) {
        return ((List<Map<String, Object>>) inspection.json().get("criteria")).stream()
                .filter(line -> id.equals(line.get("criterionId"))).findFirst().orElseThrow();
    }
}
