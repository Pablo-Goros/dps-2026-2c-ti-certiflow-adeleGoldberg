package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.adapter.certification.RegisteredCertificationPolicies;
import ar.edu.itba.dps.certification.adapter.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.adapter.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RelocateAsset;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.EvaluateIssuanceEligibility;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.application.finding.FindingService;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.RectificationConsequences;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.CorrectNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateCertificateReport;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificatePolicy;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.Map;
import java.util.Set;

public final class FullSystem {

    public final TestClock clock = TestClock.at("2026-03-01T10:00:00Z");
    public final SequentialIds ids = new SequentialIds();
    public final InMemoryAuditTrail auditTrail = new InMemoryAuditTrail();
    public final FixedActor actors = new FixedActor("operator");
    public final AuditRecorder audit = new AuditRecorder(auditTrail, actors, clock);
    public final InMemoryCatalogue catalogue = new InMemoryCatalogue();
    public final InMemorySchemaRepository schemas = new InMemorySchemaRepository();
    public final InMemoryInspectionRepository inspections = new InMemoryInspectionRepository();
    public final InMemoryFindingRepository findings = new InMemoryFindingRepository();
    public final InMemoryCertificateRepository certificates = new InMemoryCertificateRepository();
    public final DispatchingEventPublisher events = new DispatchingEventPublisher();

    public final AssetDirectory assetDirectory = new CatalogueAssetDirectory(catalogue.assets, clock);
    public final SchemaCatalog schemaCatalog = new PublishedSchemaCatalog(schemas);
    public final CriterionEvaluator evaluator = new CriterionEvaluator();
    public final RepositoryFindingQuery findingQuery = new RepositoryFindingQuery(findings);
    public final FindingService findingService =
            new FindingService(findings, ids, clock, audit);
    public final SchemaApplicability schemaApplicability =
            new SchemaApplicability();
    public final RegisterParty registerParty = new RegisterParty(catalogue.parties, ids, audit, DirectTransactions.INSTANCE);
    public final RegisterAsset registerAsset = new RegisterAsset(catalogue.assets, catalogue.parties, ids, audit, DirectTransactions.INSTANCE);
    public final RelocateAsset relocateAsset = new RelocateAsset(catalogue.assets, audit, DirectTransactions.INSTANCE);
    public final ChangeAssetResponsible changeAssetResponsible =
            new ChangeAssetResponsible(catalogue.assets, catalogue.parties, audit, DirectTransactions.INSTANCE);

    public final CreateSchema createSchema = new CreateSchema(schemas, schemaApplicability, ids, audit, DirectTransactions.INSTANCE);
    public final OpenDraft openDraft = new OpenDraft(schemas, audit, DirectTransactions.INSTANCE);
    public final EditDraft editDraft = new EditDraft(schemas, audit, DirectTransactions.INSTANCE);
    public final PublishSchemaVersion publishSchemaVersion =
            new PublishSchemaVersion(schemas, clock, audit, DirectTransactions.INSTANCE);

    public final AssignInspection assignInspection =
            new AssignInspection(inspections, assetDirectory, catalogue.parties, ids, audit, DirectTransactions.INSTANCE);
    public final StartInspection startInspection =
            new StartInspection(inspections, assetDirectory, schemaCatalog, clock, audit, actors, DirectTransactions.INSTANCE);
    public final RecordAnswer recordAnswer = new RecordAnswer(inspections, audit, actors, DirectTransactions.INSTANCE);
    public final RecordNote recordNote = new RecordNote(inspections, ids, clock, audit, actors, DirectTransactions.INSTANCE);
    public final CorrectNote correctNote = new CorrectNote(inspections, audit, actors, DirectTransactions.INSTANCE);
    public final RemoveNote removeNote = new RemoveNote(inspections, audit, actors, DirectTransactions.INSTANCE);
    public final RemoveAnswer removeAnswer = new RemoveAnswer(inspections, audit, actors, DirectTransactions.INSTANCE);
    public final RemoveEvidence removeEvidence = new RemoveEvidence(inspections, audit, actors, DirectTransactions.INSTANCE);
    public final AttachEvidence attachEvidence =
            new AttachEvidence(inspections, ids, clock, audit, actors, DirectTransactions.INSTANCE);
    public final CloseInspection closeInspection = new CloseInspection(inspections,
            assetDirectory, findingService, clock, audit, actors, DirectTransactions.INSTANCE);
    public final RectifyClosedInspection rectifyClosedInspection =
            new RectifyClosedInspection(inspections, findingService,
                    new RectificationConsequences(findingService, assetDirectory),
                    events, ids, clock, audit, actors, DirectTransactions.INSTANCE);

    public final PlanCorrectiveAction planCorrectiveAction =
            new PlanCorrectiveAction(findings, catalogue.parties, audit, clock, actors, events, DirectTransactions.INSTANCE);
    public final ReportCorrectiveActionExecution reportExecution =
            new ReportCorrectiveActionExecution(findings, clock, audit, actors, DirectTransactions.INSTANCE);
    public final VerifyCorrectiveAction verifyCorrectiveAction =
            new VerifyCorrectiveAction(findings, clock, events, audit, actors, DirectTransactions.INSTANCE);
    public final ExpireOverdueCorrectiveActions expireActions =
            new ExpireOverdueCorrectiveActions(findings, clock, events, audit, DirectTransactions.INSTANCE);

    public final RegisteredCertificationPolicies policies = TestPolicies.registry();
    public final GlobalCertificatePolicy globalPolicy =
            new AllSubsystemsMustBeInForce();
    public final CertificateFactory certificateFactory =
            new CertificateFactory(inspections, findingQuery,
                    certificates, assetDirectory, policies, ids, clock);
    public final IssueCertificate issueCertificate = new IssueCertificate(certificateFactory, certificates, audit, DirectTransactions.INSTANCE);
    public final RenewCertificate renewCertificate =
            new RenewCertificate(certificateFactory, certificates, audit, DirectTransactions.INSTANCE);
    public final ExpireDueCertificates expireCertificates =
            new ExpireDueCertificates(certificates, clock, audit, DirectTransactions.INSTANCE);
    public final EvaluateIssuanceEligibility evaluateEligibility =
            new EvaluateIssuanceEligibility(certificateFactory, DirectTransactions.INSTANCE);
    public final DeriveGlobalCertificate deriveGlobalCertificate =
            new DeriveGlobalCertificate(assetDirectory, certificates, inspections, globalPolicy, clock, DirectTransactions.INSTANCE);
    public final GenerateInspectionAct generateInspectionAct =
            new GenerateInspectionAct(
                    inspections, schemaCatalog, DirectTransactions.INSTANCE);
    public final GenerateCertificateReport generateCertificateReport =
            new GenerateCertificateReport(
                    certificates, inspections, findingQuery, DirectTransactions.INSTANCE);

    public FullSystem() {
        catalogue.parties.save(new Party(PartyId.of("executor"), "Executor",
                ar.edu.itba.dps.certification.domain.catalogue.PartyKind.PERSON));
        events.register(new CertificationReactions(certificates, inspections,
                new CertificateLifecycle(), audit, findingQuery, clock));
    }

    /** Makes the given party the authenticated user for the following operations. */
    public void actAs(Party party) {
        actors.actingAs(Actor.user(party.id(), party.name()));
    }

    /** Plans the current corrective action of a finding as its responsible, who owns that decision (RF8). */
    public Finding planAsResponsible(
            FindingId findingId, String work,
            PartyId executor, java.time.LocalDate dueDate) {
        var responsible = findings.require(findingId).responsible();
        return actingAs(responsible, () -> planCorrectiveAction.plan(findingId, work, executor, dueDate));
    }

    public <T> T actingAs(PartyId user,
            java.util.function.Supplier<T> operation) {
        var previous = actors.current();
        actors.actingAs(Actor.user(user, user.value()));
        try { return operation.get(); }
        finally { actors.actingAs(previous); }
    }

    public Party person(String name) {
        return registerParty.register(name, PartyKind.PERSON);
    }

    public Party organization(String name) {
        return registerParty.register(name, PartyKind.ORGANIZATION);
    }

    public Asset asset(String name, AssetType type, Party responsible) {
        return registerAsset.register(name, type, responsible.id(), "Building 1",
                Map.of("room", "12"), JurisdictionId.of("REFERENCE"));
    }

    public SchemaVersion publishSubsystemSchema(AssetType assetType) {
        InspectionSchema schema = createSchema.create("Facility inspection", Set.of(assetType));
        openDraft.open(schema.id());
        editDraft.addSection(schema.id(),
                Section.of("Electrical", 1, DomainWorld.electricalCriterion()));
        editDraft.addSection(schema.id(),
                Section.of("Pressure", 2, DomainWorld.pressureCriterion()));
        editDraft.addSection(schema.id(),
                Section.of("Safety", 3, DomainWorld.buildingSafetyCriterion()));
        editDraft.addSection(schema.id(),
                Section.of("Common", 4, DomainWorld.documentationCriterion()));
        return publishSchemaVersion.publish(schema.id()).publishedVersion();
    }

    public SchemaVersion publishLaboratorySchema(AssetType laboratory) {
        InspectionSchema schema =
                createSchema.create("Laboratory inspection", Set.of(laboratory));
        openDraft.open(schema.id());
        editDraft.addSection(schema.id(), Section.of("Safety", 1,
                DomainWorld.temperatureCriterion(), DomainWorld.documentationCriterion()));
        return publishSchemaVersion.publish(schema.id()).publishedVersion();
    }
}
