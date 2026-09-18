package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RelocateAsset;
import ar.edu.itba.dps.certification.application.certification.CertificationContextAssembler;
import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.certification.usecase.EvaluateIssuanceEligibility;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.application.finding.FindingService;
import ar.edu.itba.dps.certification.application.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.certification.FixedDurationValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificateIssuancePolicy;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;

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
            new FindingService(findings, ids, clock, events, audit);
    public final CertificationContextAssembler assembler =
            new CertificationContextAssembler(findingQuery, certificates, clock);
    public final RegisterParty registerParty = new RegisterParty(catalogue.parties, ids, audit);
    public final RegisterAsset registerAsset = new RegisterAsset(catalogue.assets, catalogue.parties, ids, audit);
    public final RelocateAsset relocateAsset = new RelocateAsset(catalogue.assets, audit);
    public final ChangeAssetResponsible changeAssetResponsible =
            new ChangeAssetResponsible(catalogue.assets, catalogue.parties, audit);

    public final CreateSchema createSchema = new CreateSchema(schemas, ids, audit);
    public final OpenDraft openDraft = new OpenDraft(schemas, audit);
    public final EditDraft editDraft = new EditDraft(schemas, audit);
    public final PublishSchemaVersion publishSchemaVersion =
            new PublishSchemaVersion(schemas, clock, audit);

    public final AssignInspection assignInspection =
            new AssignInspection(inspections, assetDirectory, ids, audit);
    public final StartInspection startInspection =
            new StartInspection(inspections, assetDirectory, schemaCatalog, clock, audit);
    public final RecordAnswer recordAnswer = new RecordAnswer(inspections, schemaCatalog, audit);
    public final RecordNote recordNote = new RecordNote(inspections, ids, clock, audit);
    public final AttachEvidence attachEvidence =
            new AttachEvidence(inspections, schemaCatalog, ids, clock, audit);
    public final CloseInspection closeInspection = new CloseInspection(inspections, schemaCatalog,
            assetDirectory, evaluator, findingService, clock, audit);
    public final RectifyClosedInspection rectifyClosedInspection =
            new RectifyClosedInspection(inspections, schemaCatalog, assetDirectory, evaluator,
                    findingService, events, ids, clock, audit);

    public final PlanCorrectiveAction planCorrectiveAction =
            new PlanCorrectiveAction(findings, audit);
    public final ReportCorrectiveActionExecution reportExecution =
            new ReportCorrectiveActionExecution(findings, clock, audit);
    public final VerifyCorrectiveAction verifyCorrectiveAction =
            new VerifyCorrectiveAction(findings, inspections, clock, events, audit);
    public final ExpireOverdueCorrectiveActions expireActions =
            new ExpireOverdueCorrectiveActions(findings, clock, events, audit);

    public final CertificateIssuancePolicy issuancePolicy = new CertificateIssuancePolicy();
    public final IssueCertificate issueCertificate = new IssueCertificate(inspections, certificates,
            assembler, issuancePolicy, FixedDurationValidityPolicy.ofMonths(12), ids, clock, audit);
    public final RenewCertificate renewCertificate =
            new RenewCertificate(inspections, certificates, issueCertificate, clock, audit);
    public final ExpireDueCertificates expireCertificates =
            new ExpireDueCertificates(certificates, clock, audit);
    public final EvaluateIssuanceEligibility evaluateEligibility =
            new EvaluateIssuanceEligibility(inspections, assembler, issuancePolicy);

    public FullSystem() {
        events.register(new CertificationReactions(certificates, audit));
    }

    public Party person(String name) {
        return registerParty.register(name, PartyKind.PERSON);
    }

    public Party organization(String name) {
        return registerParty.register(name, PartyKind.ORGANIZATION);
    }

    public Asset asset(String name, AssetType type, Party responsible) {
        return registerAsset.register(name, type, responsible.id(), "Building 1",
                Map.of("room", "12"));
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
