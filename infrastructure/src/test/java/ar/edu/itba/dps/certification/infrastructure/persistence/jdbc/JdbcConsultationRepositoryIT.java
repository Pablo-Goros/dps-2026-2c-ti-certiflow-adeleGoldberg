package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.assistant.usecase.DecideSuggestion;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.assistant.usecase.RequestAssistance;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;
import ar.edu.itba.dps.certification.domain.assistant.AssistantVersion;
import ar.edu.itba.dps.certification.domain.assistant.Consultation;
import ar.edu.itba.dps.certification.domain.assistant.ConsultationId;
import ar.edu.itba.dps.certification.domain.assistant.SuggestedFinding;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionDecision;
import ar.edu.itba.dps.certification.domain.assistant.SuggestionId;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.infrastructure.persistence.StaleAggregateException;
import ar.edu.itba.dps.certification.infrastructure.persistence.TestDatabase;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;
import ar.edu.itba.dps.certification.support.DirectTransactions;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FixedActor;
import ar.edu.itba.dps.certification.support.FullSystem;
import ar.edu.itba.dps.certification.support.InMemoryConsultations;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The assistant's consultations in the real database: produced by the real use cases in memory,
 * stored, and read back by a fresh set of adapters, as after a restart.
 */
class JdbcConsultationRepositoryIT {

    private static final SuggestionId CABLE = SuggestionId.of("s1");
    private static final SuggestionId VALVE = SuggestionId.of("s2");
    private static final AssistantVersion VERSION = new AssistantVersion("test", "fixed", "v1");

    private final StateCodec codec = new StateCodec();
    private final FullSystem system = new FullSystem();
    private final InMemoryConsultations consultations = new InMemoryConsultations();
    private DataSource dataSource;
    private JdbcPersistence database;
    private InspectionId inspectionId;
    private RequestAssistance request;
    private Consultation decided;
    private Consultation edited;

    @BeforeEach
    void setUp() {
        inspectionId = inspectionWithANote();
        request = new RequestAssistance(system.inspections, system.schemaCatalog,
                ignored -> new AssistantReply(VERSION, List.of(
                        new SuggestedFinding(DomainWorld.ELECTRICAL_WIRING, CriterionResult.REJECTED, Severity.CRITICAL,
                                "conductor expuesto", "la nota describe un cable pelado"),
                        new SuggestedFinding(DomainWorld.PRESSURE_VALVES, CriterionResult.OBSERVED, Severity.LOW,
                                "etiqueta borrosa", "la nota menciona la etiqueta"),
                        new SuggestedFinding(DomainWorld.DOCUMENTATION, CriterionResult.APPROVED, Severity.LOW,
                                "todo en orden", "sin observaciones"))),
                consultations, system.ids, system.clock, system.audit, system.actors, DirectTransactions.INSTANCE);
        DecideSuggestion decide = new DecideSuggestion(consultations, system.inspections,
                new RecordObservedNonConformity(system.inspections, system.ids, system.clock, system.audit,
                        system.actors, DirectTransactions.INSTANCE),
                system.clock, system.audit, system.actors, DirectTransactions.INSTANCE);

        decided = request.request(inspectionId);
        decide.accept(inspectionId, decided.id(), CABLE);
        decide.discard(inspectionId, decided.id(), VALVE, Optional.of("la etiqueta se lee"));
        system.clock.advance(Duration.ofMinutes(5));
        edited = request.request(inspectionId);
        decide.edit(inspectionId, edited.id(), VALVE, DomainWorld.PRESSURE_VALVES, CriterionResult.OBSERVED,
                Severity.MEDIUM, "etiqueta ilegible", "no se puede leer");

        dataSource = TestDatabase.dataSource();
        database = JdbcPersistence.open(dataSource);
        system.catalogue.parties.findAll().forEach(database.parties()::save);
        system.catalogue.assets.findAll().forEach(database.assets()::save);
        system.schemas.findAll().forEach(database.schemas()::save);
        system.inspections.findAll().forEach(database.inspections()::save);
        consultations.findByInspection(inspectionId).forEach(database.consultations()::save);
    }

    @Test
    @DisplayName("a consultation is read back, after a restart, exactly as it was stored, with every decision")
    void aConsultationSurvivesARestart() {
        JdbcPersistence restarted = JdbcPersistence.open(dataSource);

        Consultation stored = restarted.consultations().findById(decided.id()).orElseThrow();

        assertThat(codec.write(stored)).isEqualTo(codec.write(decided));
        assertThat(stored.version()).isEqualTo(VERSION);
        assertThat(stored.request()).isEqualTo(decided.request());
        assertThat(stored.rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo("an approving result is not a finding"));
        assertThat(stored.requireSuggestion(CABLE).decision()).get().isInstanceOf(SuggestionDecision.Accepted.class);
        assertThat(stored.requireSuggestion(VALVE).decision()).get()
                .isInstanceOfSatisfying(SuggestionDecision.Discarded.class,
                        discarded -> assertThat(discarded.reason()).contains("la etiqueta se lee"));
        assertThat(restarted.consultations().findById(edited.id()).orElseThrow().requireSuggestion(VALVE).decision())
                .get().isInstanceOfSatisfying(SuggestionDecision.Edited.class,
                        edit -> assertThat(edit.note()).isEqualTo("no se puede leer"));
    }

    @Test
    @DisplayName("an inspection's consultations are listed like the in-memory repository lists them")
    void consultationsAreListedByInspection() {
        assertThat(database.consultations().findByInspection(inspectionId))
                .extracting(codec::write)
                .containsExactlyElementsOf(consultations.findByInspection(inspectionId).stream()
                        .map(codec::write).toList());
        assertThat(database.consultations().findByInspection(InspectionId.of("another"))).isEmpty();
        assertThat(database.consultations().findById(ConsultationId.of("missing"))).isEmpty();
    }

    @Test
    @DisplayName("the observation recorded from a suggestion keeps its origin in the stored inspection")
    void theOriginOfAnObservationIsStored() {
        var record = JdbcPersistence.open(dataSource).inspections().require(inspectionId)
                .requireRecord(DomainWorld.ELECTRICAL_WIRING);

        assertThat(record.observations()).singleElement().satisfies(observation -> {
            assertThat(observation.origin()).isPresent();
            assertThat(observation.origin().orElseThrow().consultationId()).isEqualTo(decided.id().value());
            assertThat(observation.origin().orElseThrow().edited()).isFalse();
        });
    }

    @Test
    @DisplayName("a consultation cannot be stored for an inspection the database does not have")
    void aConsultationNeedsItsInspection() {
        JdbcPersistence empty = TestDatabase.create();

        assertThatThrownBy(() -> empty.consultations().save(decided)).isInstanceOf(RuntimeException.class);
        assertThat(empty.consultations().findById(decided.id())).isEmpty();
    }

    @Test
    @DisplayName("two decisions on the same consultation at once: the later save is refused, not lost")
    void concurrentDecisionsAreNotLost() throws Exception {
        Consultation open = storedOpenConsultation();
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread slow = new Thread(() -> {
            try {
                database.transactions().execute(() -> {
                    Consultation mine = database.consultations().require(open.id());
                    read.countDown();
                    await(otherCommitted);
                    mine.discard(CABLE, Optional.of("slow"), database.inspections().require(inspectionId),
                            inspector(), system.clock.now());
                    database.consultations().save(mine);
                });
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        slow.start();
        assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();

        database.transactions().execute(() -> {
            Consultation theirs = database.consultations().require(open.id());
            theirs.discard(VALVE, Optional.of("fast"), database.inspections().require(inspectionId),
                    inspector(), system.clock.now());
            database.consultations().save(theirs);
        });
        otherCommitted.countDown();
        slow.join(10_000);

        assertThat(failure.get()).isInstanceOf(StaleAggregateException.class);
        Consultation kept = database.consultations().require(open.id());
        assertThat(kept.requireSuggestion(VALVE).decided()).isTrue();
        assertThat(kept.requireSuggestion(CABLE).decided()).isFalse();
    }

    @Test
    @DisplayName("if the decision cannot be stored, the observation and the audit it would have added roll back too")
    void aDecisionIsAtomicWithItsObservationAndAudit() {
        Consultation open = storedOpenConsultation();
        int auditBefore = database.auditTrail().all().size();
        ConsultationRepository failing = new ConsultationRepository() {
            @Override public void save(Consultation consultation) { throw new IllegalStateException("disk full"); }
            @Override public Optional<Consultation> findById(ConsultationId id) { return database.consultations().findById(id); }
            @Override public List<Consultation> findByInspection(InspectionId id) { return database.consultations().findByInspection(id); }
        };
        var actor = new FixedActor(inspector().value());
        var audit = new AuditRecorder(database.auditTrail(), actor, system.clock);
        var decide = new DecideSuggestion(failing, database.inspections(),
                new RecordObservedNonConformity(database.inspections(), system.ids, system.clock, audit, actor,
                        database.transactions()),
                system.clock, audit, actor, database.transactions());

        assertThatThrownBy(() -> decide.accept(inspectionId, open.id(), CABLE)).hasMessageContaining("disk full");

        JdbcPersistence reopened = JdbcPersistence.open(dataSource);
        assertThat(reopened.inspections().require(inspectionId).requireRecord(DomainWorld.ELECTRICAL_WIRING)
                .observations()).hasSize(1);
        assertThat(reopened.consultations().require(open.id()).requireSuggestion(CABLE).decided()).isFalse();
        assertThat(reopened.auditTrail().all()).hasSize(auditBefore);
    }

    /** A consultation whose suggestions are all still undecided, stored next to the decided ones. */
    private Consultation storedOpenConsultation() {
        system.clock.advance(Duration.ofMinutes(5));
        Consultation open = request.request(inspectionId);
        database.consultations().save(open);
        return open;
    }

    private PartyId inspector() {
        return system.inspections.require(inspectionId).inspector();
    }

    private InspectionId inspectionWithANote() {
        system.publishSubsystemSchema(AssetType.FACILITY);
        Party inspector = system.person("Ana Perez");
        system.actAs(inspector);
        Party responsible = system.organization("Favaloro Foundation");
        var asset = system.registerAsset.register("Central Facility", AssetType.FACILITY, responsible.id(),
                "Building 1", Map.of("room", "12", "purpose", "research"),
                Set.of(DomainWorld.ELECTRICAL, DomainWorld.PRESSURE), JurisdictionId.of("REFERENCE"));
        InspectionId id = system.assignInspection.assign(asset.id(), inspector.id(), LocalDate.parse("2026-03-05")).id();
        system.startInspection.start(id);
        system.recordAnswer.record(id, DomainWorld.ELECTRICAL_WIRING, OptionAnswer.of("clean"));
        system.recordNote.record(id, Optional.empty(), "cable con la aislación pelada junto al tablero");
        return id;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
