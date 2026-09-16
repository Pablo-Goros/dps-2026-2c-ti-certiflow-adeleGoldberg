package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.CatalogueAssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.catalogue.usecase.ChangeAssetResponsible;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
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
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceType;
import ar.edu.itba.dps.certification.domain.schema.rule.MappedOptionsRule;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericBand;
import ar.edu.itba.dps.certification.domain.schema.rule.NumericRangeRule;
import ar.edu.itba.dps.certification.domain.schema.rule.RuleOutcome;
import ar.edu.itba.dps.certification.domain.schema.rule.YesNoRule;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class DomainWorld {

    public static final CriterionId TEMPERATURE = CriterionId.of("TEMP");
    public static final CriterionId DOCUMENTATION = CriterionId.of("DOC");
    public static final String SAFETY_MANUAL = "safety manual";

    public final TestClock clock = TestClock.at("2026-03-01T10:00:00Z");
    public final SequentialIds ids = new SequentialIds();
    public final InMemoryAuditTrail auditTrail = new InMemoryAuditTrail();
    public final FixedActor actors = new FixedActor("operator");
    public final AuditRecorder audit = new AuditRecorder(auditTrail, actors, clock);
    public final InMemoryCatalogue catalogue = new InMemoryCatalogue();
    public final InMemorySchemaRepository schemas = new InMemorySchemaRepository();
    public final InMemoryInspectionRepository inspections = new InMemoryInspectionRepository();
    public final RecordingFindingRegistry findings = new RecordingFindingRegistry();
    public final RecordingEventPublisher events = new RecordingEventPublisher();
    public final CriterionEvaluator evaluator = new CriterionEvaluator();

    public final AssetDirectory assetDirectory =
            new CatalogueAssetDirectory(catalogue.assets, clock);
    public final SchemaCatalog schemaCatalog = new PublishedSchemaCatalog(schemas);
    public final RegisterParty registerParty =
            new RegisterParty(catalogue.parties, ids, audit);
    public final RegisterAsset registerAsset =
            new RegisterAsset(catalogue.assets, catalogue.parties, ids, audit);
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
    public final RecordAnswer recordAnswer =
            new RecordAnswer(inspections, schemaCatalog, audit);
    public final AttachEvidence attachEvidence =
            new AttachEvidence(inspections, schemaCatalog, ids, clock, audit);
    public final CloseInspection closeInspection =
            new CloseInspection(inspections, schemaCatalog, assetDirectory, evaluator, findings, clock, audit);
    public final RectifyClosedInspection rectifyClosedInspection =
            new RectifyClosedInspection(inspections, schemaCatalog, assetDirectory, evaluator, findings,
                    events, ids, clock, audit);

    public Party person(String name) {
        return registerParty.register(name, PartyKind.PERSON);
    }

    public Party organization(String name) {
        return registerParty.register(name, PartyKind.ORGANIZATION);
    }

    public Asset asset(String name, AssetType type, Party responsible, String location) {
        return registerAsset.register(name, type, responsible.id(), location,
                Map.of("room", "12"));
    }

    public static Criterion temperatureCriterion() {
        return temperatureCriterion(Severity.LOW);
    }

    public static Criterion temperatureCriterion(Severity warmSeverity) {
        return new Criterion(TEMPERATURE,
                new NumericRangeRule("c", new BigDecimal("-50"), new BigDecimal("150"), List.of(
                        band("-50", true, "2", false, RuleOutcome.rejected("TEMP_LOW",
                                Severity.HIGH, "temperature below the safe range")),
                        band("2", true, "8", true, RuleOutcome.approved("TEMP_OK",
                                "temperature within the safe range")),
                        band("8", false, "25", true, RuleOutcome.observed("TEMP_WARM",
                                warmSeverity, "temperature slightly above the safe range")),
                        band("25", false, "150", true, RuleOutcome.rejected("TEMP_HIGH",
                                Severity.CRITICAL, "temperature far above the safe range")))),
                List.of());
    }

    public static Criterion documentationCriterion() {
        return new Criterion(DOCUMENTATION,
                new YesNoRule(
                        RuleOutcome.approved("DOC_OK", "documentation is current"),
                        RuleOutcome.observed("DOC_PARTIAL", Severity.LOW,
                                "documentation is incomplete")),
                List.of(EvidenceRequirement.mandatory(EvidenceType.DOCUMENT, SAFETY_MANUAL)));
    }

    public static MappedOptionsRule housekeepingRule() {
        return new MappedOptionsRule(Map.of(
                "clean", RuleOutcome.approved("HK_OK", "area is clean"),
                "untidy", RuleOutcome.observed("HK_UNTIDY", Severity.MEDIUM, "area is untidy"),
                "hazardous", RuleOutcome.rejected("HK_HAZARD", Severity.HIGH, "area is unsafe")));
    }

    private static NumericBand band(String lower, boolean lowerInclusive, String upper,
            boolean upperInclusive, RuleOutcome outcome) {
        return new NumericBand(new BigDecimal(lower), lowerInclusive, new BigDecimal(upper),
                upperInclusive, outcome);
    }

    public SchemaVersion publishLaboratorySchema(AssetType laboratory) {
        InspectionSchema schema = createSchema.create("Laboratory inspection", java.util.Set.of(laboratory));
        openDraft.open(schema.id());
        editDraft.addSection(schema.id(),
                Section.of("Safety", 1, temperatureCriterion(), documentationCriterion()));
        PublicationResult result = publishSchemaVersion.publish(schema.id());
        return result.publishedVersion();
    }
}
