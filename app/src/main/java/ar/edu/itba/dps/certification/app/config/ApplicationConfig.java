package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.adapter.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.adapter.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.application.audit.usecase.BrowseAuditTrail;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RelocateAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.SearchAssets;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.certification.port.CertificationPolicyRegistry;
import ar.edu.itba.dps.certification.application.certification.usecase.BrowseCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.EvaluateIssuanceEligibility;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.application.finding.FindingService;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.finding.usecase.BrowseFindings;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.RectificationConsequences;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.BrowseInspections;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.CorrectNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.ReassignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateCertificateReport;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateFindingsSummary;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.application.notification.NotifyCorrectiveActionExpiry;
import ar.edu.itba.dps.certification.application.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.schema.usecase.BrowseSchemas;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.DiscardDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.application.shared.usecase.PublishPendingDomainEvents;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificatePolicy;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the only place that knows every use case and which adapter backs each port.
 * It mirrors the wiring the core's integration tests use ({@code FullSystem}), with real adapters.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

    @Bean
    AuditRecorder audit(AuditTrail trail, ActorProvider actors, Clock clock) {
        return new AuditRecorder(trail, actors, clock);
    }

    @Bean
    AssetDirectory assetDirectory(AssetRepository assets, Clock clock) {
        return new CatalogueAssetDirectory(assets, clock);
    }

    @Bean
    SchemaCatalog schemaCatalog(SchemaRepository schemas) {
        return new PublishedSchemaCatalog(schemas);
    }

    @Bean
    FindingQuery findingQuery(FindingRepository findings) {
        return new RepositoryFindingQuery(findings);
    }

    @Bean
    FindingService findingService(FindingRepository findings, IdGenerator ids, Clock clock, AuditRecorder audit) {
        return new FindingService(findings, ids, clock, audit);
    }

    @Bean
    SchemaApplicability applicability() {
        return new SchemaApplicability();
    }

    @Bean
    GlobalCertificatePolicy globalPolicy() {
        return new AllSubsystemsMustBeInForce();
    }

    @Bean
    RegisterParty registerParty(PartyRepository parties, IdGenerator ids, AuditRecorder audit) {
        return new RegisterParty(parties, ids, audit);
    }

    @Bean
    RegisterAsset registerAsset(AssetRepository assets, PartyRepository parties, IdGenerator ids, AuditRecorder audit) {
        return new RegisterAsset(assets, parties, ids, audit);
    }

    @Bean
    RelocateAsset relocateAsset(AssetRepository assets, AuditRecorder audit) {
        return new RelocateAsset(assets, audit);
    }

    @Bean
    ChangeAssetResponsible changeAssetResponsible(AssetRepository assets, PartyRepository parties, AuditRecorder audit) {
        return new ChangeAssetResponsible(assets, parties, audit);
    }

    @Bean
    SearchAssets searchAssets(AssetRepository assets) {
        return new SearchAssets(assets);
    }

    @Bean
    CreateSchema createSchema(SchemaRepository schemas, SchemaApplicability applicability, IdGenerator ids, AuditRecorder audit) {
        return new CreateSchema(schemas, applicability, ids, audit);
    }

    @Bean
    OpenDraft openDraft(SchemaRepository schemas, AuditRecorder audit) {
        return new OpenDraft(schemas, audit);
    }

    @Bean
    EditDraft editDraft(SchemaRepository schemas, AuditRecorder audit) {
        return new EditDraft(schemas, audit);
    }

    @Bean
    DiscardDraft discardDraft(SchemaRepository schemas, AuditRecorder audit) {
        return new DiscardDraft(schemas, audit);
    }

    @Bean
    PublishSchemaVersion publishSchemaVersion(SchemaRepository schemas, Clock clock, AuditRecorder audit) {
        return new PublishSchemaVersion(schemas, clock, audit);
    }

    @Bean
    ChangeSchemaApplicability changeSchemaApplicability(SchemaRepository schemas, SchemaApplicability applicability, AuditRecorder audit) {
        return new ChangeSchemaApplicability(schemas, applicability, audit);
    }

    @Bean
    AssignInspection assignInspection(InspectionRepository inspections, AssetDirectory assetDirectory, PartyRepository parties, IdGenerator ids, AuditRecorder audit) {
        return new AssignInspection(inspections, assetDirectory, parties, ids, audit);
    }

    @Bean
    ReassignInspection reassignInspection(InspectionRepository inspections, PartyRepository parties, AuditRecorder audit) {
        return new ReassignInspection(inspections, parties, audit);
    }

    @Bean
    StartInspection startInspection(InspectionRepository inspections, AssetDirectory assetDirectory, SchemaCatalog schemaCatalog, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new StartInspection(inspections, assetDirectory, schemaCatalog, clock, audit, actors);
    }

    @Bean
    RecordAnswer recordAnswer(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        return new RecordAnswer(inspections, audit, actors);
    }

    @Bean
    RemoveAnswer removeAnswer(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        return new RemoveAnswer(inspections, audit, actors);
    }

    @Bean
    RecordNote recordNote(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new RecordNote(inspections, ids, clock, audit, actors);
    }

    @Bean
    CorrectNote correctNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        return new CorrectNote(inspections, audit, actors);
    }

    @Bean
    RemoveNote removeNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        return new RemoveNote(inspections, audit, actors);
    }

    @Bean
    AttachEvidence attachEvidence(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new AttachEvidence(inspections, ids, clock, audit, actors);
    }

    @Bean
    RemoveEvidence removeEvidence(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        return new RemoveEvidence(inspections, audit, actors);
    }

    @Bean
    CloseInspection closeInspection(InspectionRepository inspections, AssetDirectory assetDirectory, FindingService findingService, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new CloseInspection(inspections, assetDirectory, findingService, clock, audit, actors);
    }

    @Bean
    RectificationConsequences consequences(FindingService findingService, AssetDirectory assetDirectory) {
        return new RectificationConsequences(findingService, assetDirectory);
    }

    @Bean
    RectifyClosedInspection rectifyClosedInspection(InspectionRepository inspections, FindingService findingService, RectificationConsequences consequences, DomainEventPublisher events, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new RectifyClosedInspection(inspections, findingService, consequences, events, ids, clock, audit, actors);
    }

    @Bean
    PlanCorrectiveAction planCorrectiveAction(FindingRepository findings, AuditRecorder audit, Clock clock, ActorProvider actors, DomainEventPublisher events) {
        return new PlanCorrectiveAction(findings, audit, clock, actors, events);
    }

    @Bean
    ReportCorrectiveActionExecution reportCorrectiveActionExecution(FindingRepository findings, Clock clock, AuditRecorder audit, ActorProvider actors) {
        return new ReportCorrectiveActionExecution(findings, clock, audit, actors);
    }

    @Bean
    VerifyCorrectiveAction verifyCorrectiveAction(FindingRepository findings, Clock clock, DomainEventPublisher events, AuditRecorder audit, ActorProvider actors) {
        return new VerifyCorrectiveAction(findings, clock, events, audit, actors);
    }

    @Bean
    ExpireOverdueCorrectiveActions expireOverdueCorrectiveActions(FindingRepository findings, Clock clock, DomainEventPublisher events, AuditRecorder audit) {
        return new ExpireOverdueCorrectiveActions(findings, clock, events, audit);
    }

    @Bean
    CertificateFactory factory(InspectionRepository inspections, FindingQuery findingQuery, CertificateRepository certificates, AssetDirectory assetDirectory, CertificationPolicyRegistry policies, GlobalCertificatePolicy globalPolicy, IdGenerator ids, Clock clock) {
        return new CertificateFactory(inspections, findingQuery, certificates, assetDirectory, policies, globalPolicy, ids, clock);
    }

    @Bean
    IssueCertificate issueCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit) {
        return new IssueCertificate(factory, certificates, audit);
    }

    @Bean
    RenewCertificate renewCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit) {
        return new RenewCertificate(factory, certificates, audit);
    }

    @Bean
    ExpireDueCertificates expireDueCertificates(CertificateRepository certificates, Clock clock, AuditRecorder audit) {
        return new ExpireDueCertificates(certificates, clock, audit);
    }

    @Bean
    EvaluateIssuanceEligibility evaluateIssuanceEligibility(CertificateFactory factory) {
        return new EvaluateIssuanceEligibility(factory);
    }

    @Bean
    DeriveGlobalCertificate deriveGlobalCertificate(CertificateFactory factory) {
        return new DeriveGlobalCertificate(factory);
    }

    // Read side used by the REST controllers, so they never touch a repository directly.

    @Bean
    BrowseParties browseParties(PartyRepository parties) {
        return new BrowseParties(parties);
    }

    @Bean
    BrowseSchemas browseSchemas(SchemaRepository schemas) {
        return new BrowseSchemas(schemas);
    }

    @Bean
    BrowseInspections browseInspections(InspectionRepository inspections, SchemaCatalog schemaCatalog) {
        return new BrowseInspections(inspections, schemaCatalog);
    }

    @Bean
    BrowseFindings browseFindings(FindingRepository findings) {
        return new BrowseFindings(findings);
    }

    @Bean
    BrowseCertificates browseCertificates(CertificateRepository certificates) {
        return new BrowseCertificates(certificates);
    }

    @Bean
    BrowseAuditTrail browseAuditTrail(AuditTrail trail) {
        return new BrowseAuditTrail(trail);
    }

    @Bean
    GenerateInspectionAct generateInspectionAct(InspectionRepository inspections, SchemaCatalog schemaCatalog) {
        return new GenerateInspectionAct(inspections, schemaCatalog);
    }

    @Bean
    GenerateCertificateReport generateCertificateReport(CertificateRepository certificates, InspectionRepository inspections, FindingQuery findingQuery) {
        return new GenerateCertificateReport(certificates, inspections, findingQuery);
    }

    @Bean
    GenerateFindingsSummary generateFindingsSummary(FindingQuery findingQuery) {
        return new GenerateFindingsSummary(findingQuery);
    }

    @Bean
    PublishPendingDomainEvents publishPendingDomainEvents(InspectionRepository inspections, FindingRepository findings, DomainEventPublisher events) {
        return new PublishPendingDomainEvents(inspections, findings, events);
    }

    @Bean
    CertificateLifecycle certificateLifecycle() {
        return new CertificateLifecycle();
    }

    @Bean
    CertificationReactions certificationReactions(CertificateRepository certificates,
            InspectionRepository inspections, CertificateLifecycle lifecycle, AuditRecorder audit,
            FindingQuery findingQuery, Clock clock) {
        return new CertificationReactions(certificates, inspections, lifecycle, audit, findingQuery, clock);
    }

    /** Tells the finding's responsible when a corrective action expires; a failing channel never blocks the event. */
    @Bean
    NotifyCorrectiveActionExpiry notifyCorrectiveActionExpiry(FindingRepository findings, PartyRepository parties,
            NotificationSender sender) {
        System.Logger log = System.getLogger(NotifyCorrectiveActionExpiry.class.getName());
        return new NotifyCorrectiveActionExpiry(findings, parties, sender, (notification, failure) ->
                log.log(System.Logger.Level.WARNING, "could not notify " + notification.recipientId()
                        + " about '" + notification.subject() + "'", failure));
    }
}
