package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.adapter.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.adapter.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.finding.FindingService;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.RectificationConsequences;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.JdbcPersistence;
import ar.edu.itba.dps.certification.support.DispatchingEventPublisher;
import ar.edu.itba.dps.certification.support.DomainWorld;
import ar.edu.itba.dps.certification.support.FixedActor;
import ar.edu.itba.dps.certification.support.SequentialIds;
import ar.edu.itba.dps.certification.support.TestClock;
import ar.edu.itba.dps.certification.support.TestPolicies;

import java.util.Set;
import java.util.function.Supplier;

/**
 * The application wired to the real repositories. It is the same composition the REST layer will
 * need, with a clock, identifiers and actor under the test's control.
 */
final class JdbcSystem {

    final JdbcPersistence persistence;
    final TestClock clock = TestClock.at("2026-03-01T10:00:00Z");
    final FixedActor actors = new FixedActor("operator");
    final SequentialIds ids = new SequentialIds();
    final DispatchingEventPublisher events = new DispatchingEventPublisher();
    final RegisterParty registerParty;
    final RegisterAsset registerAsset;
    final CreateSchema createSchema;
    final OpenDraft openDraft;
    final EditDraft editDraft;
    final PublishSchemaVersion publishSchemaVersion;
    final AssignInspection assignInspection;
    final StartInspection startInspection;
    final RecordAnswer recordAnswer;
    final AttachEvidence attachEvidence;
    final CloseInspection closeInspection;
    final RectifyClosedInspection rectifyClosedInspection;
    final PlanCorrectiveAction planCorrectiveAction;
    final ReportCorrectiveActionExecution reportExecution;
    final VerifyCorrectiveAction verifyCorrectiveAction;
    final IssueCertificate issueCertificate;

    JdbcSystem(JdbcPersistence persistence) {
        this.persistence = persistence;
        var audit = new AuditRecorder(persistence.auditTrail(), actors, clock);
        AssetDirectory assetDirectory = new CatalogueAssetDirectory(persistence.assets(), clock);
        SchemaCatalog schemaCatalog = new PublishedSchemaCatalog(persistence.schemas());
        var findingQuery = new RepositoryFindingQuery(persistence.findings());
        var findingService = new FindingService(persistence.findings(), ids, clock, audit);
        var applicability = new SchemaApplicability();
        var certificateFactory = new CertificateFactory(persistence.inspections(), findingQuery,
                persistence.certificates(), assetDirectory, TestPolicies.registry(),
                new AllSubsystemsMustBeInForce(), ids, clock);

        registerParty = new RegisterParty(persistence.parties(), ids, audit);
        registerAsset = new RegisterAsset(persistence.assets(), persistence.parties(), ids, audit);
        createSchema = new CreateSchema(persistence.schemas(), applicability, ids, audit);
        openDraft = new OpenDraft(persistence.schemas(), audit);
        editDraft = new EditDraft(persistence.schemas(), audit);
        publishSchemaVersion = new PublishSchemaVersion(persistence.schemas(), clock, audit);
        assignInspection = new AssignInspection(persistence.inspections(), assetDirectory,
                persistence.parties(), ids, audit);
        startInspection = new StartInspection(persistence.inspections(), assetDirectory, schemaCatalog,
                clock, audit, actors);
        recordAnswer = new RecordAnswer(persistence.inspections(), audit, actors);
        attachEvidence = new AttachEvidence(persistence.inspections(), ids, clock, audit, actors);
        closeInspection = new CloseInspection(persistence.inspections(), assetDirectory, findingService,
                clock, audit, actors);
        rectifyClosedInspection = new RectifyClosedInspection(persistence.inspections(), findingService,
                new RectificationConsequences(findingService, assetDirectory), events, ids, clock, audit, actors);
        planCorrectiveAction = new PlanCorrectiveAction(persistence.findings(), audit, clock, actors, events);
        reportExecution = new ReportCorrectiveActionExecution(persistence.findings(), clock, audit, actors);
        verifyCorrectiveAction = new VerifyCorrectiveAction(persistence.findings(), clock, events, audit, actors);
        issueCertificate = new IssueCertificate(certificateFactory, persistence.certificates(), audit);
        events.register(new CertificationReactions(persistence.certificates(), persistence.inspections(),
                new CertificateLifecycle(), audit, findingQuery, clock));
    }

    /** Runs one use case the way a request does: in a single transaction. */
    <T> T tx(Supplier<T> useCase) {
        return persistence.transactions().execute(useCase);
    }

    void tx(Runnable useCase) {
        persistence.transactions().execute(useCase);
    }

    void actAs(Party party) {
        actors.actingAs(Actor.user(party.id(), party.name()));
    }

    <T> T actingAs(PartyId user, Supplier<T> operation) {
        var previous = actors.current();
        actors.actingAs(Actor.user(user, user.value()));
        try {
            return operation.get();
        } finally {
            actors.actingAs(previous);
        }
    }

    void publishLaboratorySchema() {
        InspectionSchema schema = createSchema.create("Laboratory inspection", Set.of(AssetType.LABORATORY));
        openDraft.open(schema.id());
        editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(), DomainWorld.documentationCriterion()));
        publishSchemaVersion.publish(schema.id());
    }
}
