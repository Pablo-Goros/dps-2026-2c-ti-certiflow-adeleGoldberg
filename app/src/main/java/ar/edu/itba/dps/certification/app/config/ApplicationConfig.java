package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.adapter.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.adapter.finding.RepositoryFindingQuery;
import ar.edu.itba.dps.certification.adapter.schema.PublishedSchemaCatalog;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.audit.usecase.BrowseAuditTrail;
import ar.edu.itba.dps.certification.application.catalogue.usecase.BrowseParties;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RelocateAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.SearchAssets;
import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.certification.usecase.BrowseCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.EvaluateIssuanceEligibility;
import ar.edu.itba.dps.certification.application.certification.usecase.ExpireDueCertificates;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.RenewCertificate;
import ar.edu.itba.dps.certification.application.finding.FindingService;
import ar.edu.itba.dps.certification.application.finding.usecase.BrowseFindings;
import ar.edu.itba.dps.certification.application.finding.usecase.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.finding.usecase.ReportCorrectiveActionExecution;
import ar.edu.itba.dps.certification.application.finding.usecase.VerifyCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.RectificationConsequences;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.BrowseInspections;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.CorrectNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.ReassignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.application.inspection.usecase.RectifyClosedInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.RemoveNote;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.WithdrawObservedNonConformity;
import ar.edu.itba.dps.certification.application.notification.NotifyCorrectiveActionExpiry;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateCertificateReport;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateFindingsSummary;
import ar.edu.itba.dps.certification.application.report.usecase.GenerateInspectionAct;
import ar.edu.itba.dps.certification.application.schema.usecase.BrowseSchemas;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.DiscardDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.schema.usecase.WithdrawScheduledSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.usecase.PublishPendingDomainEvents;
import ar.edu.itba.dps.certification.domain.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetRepository;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.certification.CertificateLifecycle;
import ar.edu.itba.dps.certification.domain.certification.derivation.AllSubsystemsMustBeInForce;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificatePolicy;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.certification.port.CertificationPolicyRegistry;
import ar.edu.itba.dps.certification.domain.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.domain.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.notification.port.NotificationSender;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.schema.SchemaApplicability;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

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
    RegisterParty registerParty(PartyRepository parties, IdGenerator ids, AuditRecorder audit, Transactions transactions) {
        return new RegisterParty(parties, ids, audit, transactions);
    }

    @Bean
    RegisterAsset registerAsset(AssetRepository assets, PartyRepository parties, IdGenerator ids, AuditRecorder audit, Transactions transactions) {
        return new RegisterAsset(assets, parties, ids, audit, transactions);
    }

    @Bean
    RelocateAsset relocateAsset(AssetRepository assets, AuditRecorder audit, Transactions transactions) {
        return new RelocateAsset(assets, audit, transactions);
    }

    @Bean
    ChangeAssetResponsible changeAssetResponsible(AssetRepository assets, PartyRepository parties, AuditRecorder audit, Transactions transactions) {
        return new ChangeAssetResponsible(assets, parties, audit, transactions);
    }

    @Bean
    SearchAssets searchAssets(AssetRepository assets, Transactions transactions) {
        return new SearchAssets(assets, transactions);
    }

    @Bean
    CreateSchema createSchema(SchemaRepository schemas, SchemaApplicability applicability, IdGenerator ids, AuditRecorder audit, Transactions transactions) {
        return new CreateSchema(schemas, applicability, ids, audit, transactions);
    }

    @Bean
    OpenDraft openDraft(SchemaRepository schemas, AuditRecorder audit, Transactions transactions) {
        return new OpenDraft(schemas, audit, transactions);
    }

    @Bean
    EditDraft editDraft(SchemaRepository schemas, AuditRecorder audit, Transactions transactions) {
        return new EditDraft(schemas, audit, transactions);
    }

    @Bean
    DiscardDraft discardDraft(SchemaRepository schemas, AuditRecorder audit, Transactions transactions) {
        return new DiscardDraft(schemas, audit, transactions);
    }

    @Bean
    PublishSchemaVersion publishSchemaVersion(SchemaRepository schemas, Clock clock, AuditRecorder audit, Transactions transactions) {
        return new PublishSchemaVersion(schemas, clock, audit, transactions);
    }

    @Bean
    WithdrawScheduledSchemaVersion withdrawScheduledSchemaVersion(SchemaRepository schemas, Clock clock,
            AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new WithdrawScheduledSchemaVersion(schemas, clock, audit, actors, transactions);
    }

    @Bean
    ChangeSchemaApplicability changeSchemaApplicability(SchemaRepository schemas, SchemaApplicability applicability, AuditRecorder audit, Clock clock, Transactions transactions) {
        return new ChangeSchemaApplicability(schemas, applicability, audit, clock, transactions);
    }

    @Bean
    AssignInspection assignInspection(InspectionRepository inspections, AssetDirectory assetDirectory, PartyRepository parties, IdGenerator ids, AuditRecorder audit, Transactions transactions) {
        return new AssignInspection(inspections, assetDirectory, parties, ids, audit, transactions);
    }

    @Bean
    ReassignInspection reassignInspection(InspectionRepository inspections, PartyRepository parties, AuditRecorder audit, Transactions transactions) {
        return new ReassignInspection(inspections, parties, audit, transactions);
    }

    @Bean
    StartInspection startInspection(InspectionRepository inspections, AssetDirectory assetDirectory, SchemaCatalog schemaCatalog, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new StartInspection(inspections, assetDirectory, schemaCatalog, clock, audit, actors, transactions);
    }

    @Bean
    RecordAnswer recordAnswer(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RecordAnswer(inspections, audit, actors, transactions);
    }

    @Bean
    RemoveAnswer removeAnswer(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RemoveAnswer(inspections, audit, actors, transactions);
    }

    @Bean
    RecordNote recordNote(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RecordNote(inspections, ids, clock, audit, actors, transactions);
    }

    @Bean
    CorrectNote correctNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new CorrectNote(inspections, audit, actors, transactions);
    }

    @Bean
    RemoveNote removeNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RemoveNote(inspections, audit, actors, transactions);
    }

    @Bean
    RecordObservedNonConformity recordObservedNonConformity(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RecordObservedNonConformity(inspections, ids, clock, audit, actors, transactions);
    }

    @Bean
    WithdrawObservedNonConformity withdrawObservedNonConformity(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new WithdrawObservedNonConformity(inspections, audit, actors, transactions);
    }

    @Bean
    AttachEvidence attachEvidence(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new AttachEvidence(inspections, ids, clock, audit, actors, transactions);
    }

    @Bean
    RemoveEvidence removeEvidence(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RemoveEvidence(inspections, audit, actors, transactions);
    }

    @Bean
    CloseInspection closeInspection(InspectionRepository inspections, AssetDirectory assetDirectory, FindingService findingService, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new CloseInspection(inspections, assetDirectory, findingService, clock, audit, actors, transactions);
    }

    @Bean
    RectificationConsequences consequences(FindingService findingService, AssetDirectory assetDirectory) {
        return new RectificationConsequences(findingService, assetDirectory);
    }

    @Bean
    RectifyClosedInspection rectifyClosedInspection(InspectionRepository inspections, FindingService findingService, RectificationConsequences consequences, DomainEventPublisher events, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RectifyClosedInspection(inspections, findingService, consequences, events, ids, clock, audit, actors, transactions);
    }

    @Bean
    PlanCorrectiveAction planCorrectiveAction(FindingRepository findings, PartyRepository parties, AuditRecorder audit, Clock clock, ActorProvider actors, DomainEventPublisher events, Transactions transactions) {
        return new PlanCorrectiveAction(findings, parties, audit, clock, actors, events, transactions);
    }

    @Bean
    ReportCorrectiveActionExecution reportCorrectiveActionExecution(FindingRepository findings, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new ReportCorrectiveActionExecution(findings, clock, audit, actors, transactions);
    }

    @Bean
    VerifyCorrectiveAction verifyCorrectiveAction(FindingRepository findings, Clock clock, DomainEventPublisher events, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new VerifyCorrectiveAction(findings, clock, events, audit, actors, transactions);
    }

    @Bean
    ExpireOverdueCorrectiveActions expireOverdueCorrectiveActions(FindingRepository findings, Clock clock, DomainEventPublisher events, AuditRecorder audit, Transactions transactions) {
        return new ExpireOverdueCorrectiveActions(findings, clock, events, audit, transactions);
    }

    @Bean
    CertificateFactory factory(InspectionRepository inspections, FindingQuery findingQuery, CertificateRepository certificates, AssetDirectory assetDirectory, CertificationPolicyRegistry policies, IdGenerator ids, Clock clock) {
        return new CertificateFactory(inspections, findingQuery, certificates, assetDirectory, policies, ids, clock);
    }

    @Bean
    IssueCertificate issueCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit, Transactions transactions) {
        return new IssueCertificate(factory, certificates, audit, transactions);
    }

    @Bean
    RenewCertificate renewCertificate(CertificateFactory factory, CertificateRepository certificates, AuditRecorder audit, Transactions transactions) {
        return new RenewCertificate(factory, certificates, audit, transactions);
    }

    @Bean
    ExpireDueCertificates expireDueCertificates(CertificateRepository certificates, Clock clock, AuditRecorder audit, Transactions transactions) {
        return new ExpireDueCertificates(certificates, clock, audit, transactions);
    }

    @Bean
    EvaluateIssuanceEligibility evaluateIssuanceEligibility(CertificateFactory factory, Transactions transactions) {
        return new EvaluateIssuanceEligibility(factory, transactions);
    }

    @Bean
    DeriveGlobalCertificate deriveGlobalCertificate(AssetDirectory assets, CertificateRepository certificates,
            InspectionRepository inspections, GlobalCertificatePolicy policy, Clock clock, Transactions transactions) {
        return new DeriveGlobalCertificate(assets, certificates, inspections, policy, clock, transactions);
    }

    // Read side used by the REST controllers, so they never touch a repository directly.

    @Bean
    BrowseParties browseParties(PartyRepository parties, Transactions transactions) {
        return new BrowseParties(parties, transactions);
    }

    @Bean
    BrowseSchemas browseSchemas(SchemaRepository schemas, Transactions transactions) {
        return new BrowseSchemas(schemas, transactions);
    }

    @Bean
    BrowseInspections browseInspections(InspectionRepository inspections, SchemaCatalog schemaCatalog, Transactions transactions) {
        return new BrowseInspections(inspections, schemaCatalog, transactions);
    }

    @Bean
    BrowseFindings browseFindings(FindingRepository findings, Transactions transactions) {
        return new BrowseFindings(findings, transactions);
    }

    @Bean
    BrowseCertificates browseCertificates(CertificateRepository certificates, Transactions transactions) {
        return new BrowseCertificates(certificates, transactions);
    }

    @Bean
    BrowseAuditTrail browseAuditTrail(AuditTrail trail, Transactions transactions) {
        return new BrowseAuditTrail(trail, transactions);
    }

    @Bean
    GenerateInspectionAct generateInspectionAct(InspectionRepository inspections, SchemaCatalog schemaCatalog, Transactions transactions) {
        return new GenerateInspectionAct(inspections, schemaCatalog, transactions);
    }

    @Bean
    GenerateCertificateReport generateCertificateReport(CertificateRepository certificates, InspectionRepository inspections, FindingQuery findingQuery, Transactions transactions) {
        return new GenerateCertificateReport(certificates, inspections, findingQuery, transactions);
    }

    @Bean
    GenerateFindingsSummary generateFindingsSummary(FindingQuery findingQuery, Transactions transactions) {
        return new GenerateFindingsSummary(findingQuery, transactions);
    }

    @Bean
    PublishPendingDomainEvents publishPendingDomainEvents(InspectionRepository inspections, FindingRepository findings, DomainEventPublisher events, Transactions transactions) {
        return new PublishPendingDomainEvents(inspections, findings, events, transactions);
    }

    @Bean
    ar.edu.itba.dps.certification.domain.shared.port.inbound.RunMaintenance maintenanceService(
            ar.edu.itba.dps.certification.domain.certification.port.inbound.ExpireDueCertificates certificates,
            ar.edu.itba.dps.certification.domain.finding.port.inbound.ExpireOverdueCorrectiveActions actions,
            ar.edu.itba.dps.certification.domain.shared.port.inbound.PublishPendingDomainEvents republish,
            ar.edu.itba.dps.certification.domain.shared.port.OutboxOperations outbox) {
        return new ar.edu.itba.dps.certification.application.shared.MaintenanceService(certificates, actions, republish, outbox);
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
