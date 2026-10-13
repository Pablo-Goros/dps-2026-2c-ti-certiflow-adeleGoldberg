package ar.edu.itba.dps.certification.app.demo;

import ar.edu.itba.dps.certification.app.config.RequestActor;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterAsset;
import ar.edu.itba.dps.certification.application.catalogue.usecase.RegisterParty;
import ar.edu.itba.dps.certification.application.catalogue.usecase.SearchAssets;
import ar.edu.itba.dps.certification.application.certification.usecase.DeriveGlobalCertificate;
import ar.edu.itba.dps.certification.application.certification.usecase.IssueCertificate;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.finding.usecase.PlanCorrectiveAction;
import ar.edu.itba.dps.certification.application.inspection.usecase.AssignInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.AttachEvidence;
import ar.edu.itba.dps.certification.application.inspection.usecase.CloseInspection;
import ar.edu.itba.dps.certification.application.inspection.usecase.RecordAnswer;
import ar.edu.itba.dps.certification.application.inspection.usecase.StartInspection;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.catalogue.Asset;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.Section;
import ar.edu.itba.dps.certification.domain.shared.Actor;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;
import ar.edu.itba.dps.certification.domain.shared.answer.Measurement;
import ar.edu.itba.dps.certification.domain.shared.answer.OptionAnswer;
import ar.edu.itba.dps.certification.domain.shared.answer.YesNoAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Fills an empty database with the scenario the delivery asks to demonstrate: one schema in two
 * versions, the second effective in the future, an inspection frozen on the first, findings of
 * different severities, a certificate per subsystem with its derived global one, and the same
 * inspection outcome decided under two jurisdictions.
 *
 * <p>It drives the real use cases rather than inserting rows. Everything the demo shows — the
 * evaluations, the findings and their severities, the certificates and their validity — is state
 * the domain computes, so writing it as SQL would mean recomputing by hand what the rules already
 * decide, and would silently drift the day a rule changes.
 */
@Component
@Profile("demo")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final JurisdictionId BUENOS_AIRES = JurisdictionId.of("AR-BA");
    private static final JurisdictionId CORDOBA = JurisdictionId.of("AR-CBA");
    private static final JurisdictionId REFERENCE = JurisdictionId.of("REFERENCE");

    /** What the inspector found, so the same walkthrough can produce different severities. */
    private record Findings(String insulationMegohms, String reliefValve) {

        /**
         * Nothing worse than a medium observation. Two assets inspected this way differ only in
         * their jurisdiction, so whatever separates their certificates is the policy deciding.
         */
        static Findings upToMedium() {
            return new Findings("12", "worn");
        }

        /**
         * Weak insulation, a high observation. Only a jurisdiction that blocks on no severity
         * still certifies an asset found this way.
         */
        static Findings withHighSeverity() {
            return new Findings("3", "worn");
        }
    }

    private static final Duration SECOND_VERSION_TAKES_EFFECT_IN = Duration.ofDays(30);

    private final RequestActor actors;
    private final Clock clock;
    private final SearchAssets assets;
    private final RegisterParty registerParty;
    private final RegisterAsset registerAsset;
    private final CreateSchema createSchema;
    private final OpenDraft openDraft;
    private final EditDraft editDraft;
    private final PublishSchemaVersion publishSchemaVersion;
    private final AssignInspection assignInspection;
    private final StartInspection startInspection;
    private final RecordAnswer recordAnswer;
    private final AttachEvidence attachEvidence;
    private final CloseInspection closeInspection;
    private final PlanCorrectiveAction planCorrectiveAction;
    private final FindingQuery findings;
    private final IssueCertificate issueCertificate;
    private final DeriveGlobalCertificate deriveGlobalCertificate;

    DemoDataSeeder(RequestActor actors, Clock clock, SearchAssets assets,
            RegisterParty registerParty, RegisterAsset registerAsset, CreateSchema createSchema,
            OpenDraft openDraft, EditDraft editDraft, PublishSchemaVersion publishSchemaVersion,
            AssignInspection assignInspection, StartInspection startInspection,
            RecordAnswer recordAnswer, AttachEvidence attachEvidence,
            CloseInspection closeInspection, PlanCorrectiveAction planCorrectiveAction,
            FindingQuery findings, IssueCertificate issueCertificate,
            DeriveGlobalCertificate deriveGlobalCertificate) {
        this.actors = actors;
        this.clock = clock;
        this.assets = assets;
        this.registerParty = registerParty;
        this.registerAsset = registerAsset;
        this.createSchema = createSchema;
        this.openDraft = openDraft;
        this.editDraft = editDraft;
        this.publishSchemaVersion = publishSchemaVersion;
        this.assignInspection = assignInspection;
        this.startInspection = startInspection;
        this.recordAnswer = recordAnswer;
        this.attachEvidence = attachEvidence;
        this.closeInspection = closeInspection;
        this.planCorrectiveAction = planCorrectiveAction;
        this.findings = findings;
        this.issueCertificate = issueCertificate;
        this.deriveGlobalCertificate = deriveGlobalCertificate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!assets.byType(AssetType.FACILITY).isEmpty()) {
            LOG.info("demo data is already present, leaving the database as it is");
            return;
        }
        try {
            seed();
        } finally {
            actors.clear();
        }
    }

    private void seed() {
        Party inspector = asSystem(() -> registerParty.register("Ana Pérez", PartyKind.PERSON));
        Party northern = asSystem(() ->
                registerParty.register("Northern Industrial Park", PartyKind.ORGANIZATION));
        Party cordoba = asSystem(() ->
                registerParty.register("Córdoba Logistics Hub", PartyKind.ORGANIZATION));
        Party reference = asSystem(() ->
                registerParty.register("Reference Testing Grounds", PartyKind.ORGANIZATION));

        Asset buenosAires = asSystem(() -> registerAsset.register("Buenos Aires Plant",
                AssetType.FACILITY, northern.id(), "Panamericana km 32",
                Map.of("room", "Main hall", "purpose", "assembly"), BUENOS_AIRES));
        Asset cordobaPlant = asSystem(() -> registerAsset.register("Córdoba Plant",
                AssetType.FACILITY, cordoba.id(), "Ruta 9 km 701",
                Map.of("room", "Main hall", "purpose", "logistics"), CORDOBA));
        Asset referencePlant = asSystem(() -> registerAsset.register("Reference Plant",
                AssetType.FACILITY, reference.id(), "Av. Siempreviva 742",
                Map.of("room", "Main hall", "purpose", "testing"), REFERENCE));

        SchemaId schema = publishBothVersions();
        LOG.info("published {} v1 and v2, the second effective in {} days",
                DemoSchema.NAME, SECOND_VERSION_TAKES_EFFECT_IN.toDays());

        certifyByParts(buenosAires, inspector, northern.id(), Findings.upToMedium());
        certifyByParts(cordobaPlant, inspector, cordoba.id(), Findings.upToMedium());
        certifyByParts(referencePlant, inspector, reference.id(), Findings.withHighSeverity());

        LOG.info("demo data ready: schema {}, assets {}, {} and {}", schema.value(),
                buenosAires.name(), cordobaPlant.name(), referencePlant.name());
    }

    /**
     * The second version is published with a future effective date, so inspections started now
     * still freeze the first one and the act keeps showing the rules that were in force.
     */
    private SchemaId publishBothVersions() {
        SchemaId schema = asSystem(() ->
                createSchema.create(DemoSchema.NAME, Set.of(AssetType.FACILITY))).id();

        asSystem(() -> openDraft.open(schema));
        DemoSchema.firstVersion().forEach(section -> asSystem(() -> editDraft.addSection(schema, section)));
        requirePublished(asSystem(() -> publishSchemaVersion.publish(schema)));

        asSystem(() -> openDraft.open(schema));
        DemoSchema.secondVersion().forEach(section -> {
            asSystem(() -> editDraft.removeSection(schema, section.name()));
            asSystem(() -> editDraft.addSection(schema, section));
        });
        requirePublished(asSystem(() -> publishSchemaVersion.publish(schema,
                clock.now().plus(SECOND_VERSION_TAKES_EFFECT_IN))));
        return schema;
    }

    /**
     * One inspection whose answers produce a low, a medium and a high severity finding, closed and
     * then certified one subsystem at a time. The outcome is identical for both assets; what
     * differs is the jurisdiction deciding on it.
     */
    private void certifyByParts(Asset asset, Party inspector, PartyId responsible, Findings found) {
        InspectionId inspection = asSystem(() -> assignInspection
                .assign(asset.id(), inspector.id(), LocalDate.now().plusDays(7))).id();

        asUser(inspector, () -> {
            startInspection.start(inspection);
            answer(inspection, DemoSchema.INSULATION,
                    Measurement.of(found.insulationMegohms(), "MOhm"));
            answer(inspection, DemoSchema.GROUNDING, YesNoAnswer.yes());
            answer(inspection, DemoSchema.RELIEF_VALVE, OptionAnswer.of(found.reliefValve()));
            answer(inspection, DemoSchema.VESSEL_TEST, Measurement.of("9", "bar"));
            answer(inspection, DemoSchema.EXITS, OptionAnswer.of("clear"));
            answer(inspection, DemoSchema.EXTINGUISHERS, YesNoAnswer.no());
            answer(inspection, DemoSchema.MANUAL, YesNoAnswer.yes());
            answer(inspection, DemoSchema.TRAINING, Measurement.of("2", "h"));
            attachEvidence.attach(inspection, DemoSchema.MANUAL, DemoSchema.SAFETY_MANUAL,
                    "file://demo/safety-manual.pdf");
            closeInspection.close(inspection);
        });

        planEveryAction(inspection, responsible);

        asSystem(() -> {
            for (var subsystem : asset.subsystems()) {
                issueCertificate.issuePartial(inspection, subsystem);
            }
            return deriveGlobalCertificate.deriveFor(inspection);
        });
    }

    /** Every finding needs a planned action with a deadline before a certificate can be issued. */
    private void planEveryAction(InspectionId inspection, PartyId responsible) {
        for (Finding finding : findings.findingsOf(inspection)) {
            asUser(responsible, "responsible", () -> planCorrectiveAction.plan(finding.id(),
                    "corregir " + finding.criterionId(), responsible, LocalDate.now().plusDays(45)));
        }
    }

    private void answer(InspectionId inspection, CriterionId criterion, Answer answer) {
        recordAnswer.record(inspection, criterion, answer);
    }

    private static void requirePublished(PublicationResult result) {
        if (!result.published()) {
            throw new IllegalStateException(
                    "the demo schema was refused: " + String.join("; ", result.violations()));
        }
    }

    private <T> T asSystem(Supplier<T> operation) {
        actors.clear();
        return operation.get();
    }

    private void asSystem(Runnable operation) {
        actors.clear();
        operation.run();
    }

    private void asUser(Party user, Runnable operation) {
        asUser(user.id(), user.name(), operation);
    }

    private void asUser(PartyId user, String name, Runnable operation) {
        actors.set(Actor.user(user, name));
        try {
            operation.run();
        } finally {
            actors.clear();
        }
    }
}
