package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.ApplicabilityRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.CreateSchemaRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.PublishRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.SchemaResponse;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.SectionDto;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.TransferRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.VersionDto;
import ar.edu.itba.dps.certification.application.schema.usecase.BrowseSchemas;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.DiscardDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.Transactions;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Inspection schemas (F2): drafts, versions with a future effective date, and which asset types they cover. */
@RestController
@RequestMapping("/api/schemas")
class SchemaController {

    private final CreateSchema createSchema;
    private final OpenDraft openDraft;
    private final EditDraft editDraft;
    private final DiscardDraft discardDraft;
    private final PublishSchemaVersion publishSchema;
    private final ChangeSchemaApplicability applicability;
    private final BrowseSchemas schemas;
    private final Clock clock;
    private final Transactions transactions;

    SchemaController(CreateSchema createSchema, OpenDraft openDraft, EditDraft editDraft,
            DiscardDraft discardDraft, PublishSchemaVersion publishSchema,
            ChangeSchemaApplicability applicability, BrowseSchemas schemas, Clock clock,
            Transactions transactions) {
        this.createSchema = createSchema;
        this.openDraft = openDraft;
        this.editDraft = editDraft;
        this.discardDraft = discardDraft;
        this.publishSchema = publishSchema;
        this.applicability = applicability;
        this.schemas = schemas;
        this.clock = clock;
        this.transactions = transactions;
    }

    @PostMapping
    ResponseEntity<SchemaResponse> create(@RequestBody CreateSchemaRequest request) {
        InspectionSchema schema = transactions.execute(
                () -> createSchema.create(request.name(), request.assetTypes()));
        return ResponseEntity.created(URI.create("/api/schemas/" + schema.id().value()))
                .body(view(schema));
    }

    @GetMapping
    List<SchemaResponse> list() {
        return schemas.all().stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    SchemaResponse get(@PathVariable("id") String id) {
        return view(existing(id));
    }

    @GetMapping("/{id}/versions/{number}")
    VersionDto version(@PathVariable("id") String id, @PathVariable("number") int number) {
        return existing(id).findVersion(number)
                .map(VersionDto::of)
                .orElseThrow(() -> new NotFoundException("schema " + id + " has no version " + number));
    }

    /**
     * The version that was (or will be) in force at {@code at} (an ISO-8601 instant, default now).
     * A version published with a future {@code effectiveFrom} is not in force before that moment.
     */
    @GetMapping("/{id}/effective-version")
    VersionDto effectiveVersion(@PathVariable("id") String id,
            @RequestParam(name = "at", required = false) String at) {
        Instant moment = at == null || at.isBlank() ? clock.now() : parseInstant(at);
        return schemas.versionInForceAt(SchemaId.of(id), moment)
                .map(VersionDto::of)
                .orElseThrow(() -> {
                    existing(id);
                    return new NotFoundException("schema " + id + " had no version in force at " + moment);
                });
    }

    @PostMapping("/{id}/draft")
    SchemaResponse openDraft(@PathVariable("id") String id) {
        existing(id);
        transactions.execute(() -> openDraft.open(SchemaId.of(id)));
        return view(existing(id));
    }

    @DeleteMapping("/{id}/draft")
    SchemaResponse discardDraft(@PathVariable("id") String id) {
        existing(id);
        transactions.execute(() -> discardDraft.discard(SchemaId.of(id)));
        return view(existing(id));
    }

    @PostMapping("/{id}/draft/sections")
    SchemaResponse addSection(@PathVariable("id") String id, @RequestBody SectionDto section) {
        existing(id);
        transactions.execute(() -> editDraft.addSection(SchemaId.of(id), section.toDomain()));
        return view(existing(id));
    }

    @DeleteMapping("/{id}/draft/sections/{name}")
    SchemaResponse removeSection(@PathVariable("id") String id, @PathVariable("name") String name) {
        existing(id);
        transactions.execute(() -> editDraft.removeSection(SchemaId.of(id), name));
        return view(existing(id));
    }

    /** Publishes the draft. A future {@code effectiveFrom} schedules the version without activating it. */
    @PostMapping("/{id}/publish")
    ResponseEntity<VersionDto> publish(@PathVariable("id") String id,
            @RequestBody(required = false) PublishRequest request) {
        existing(id);
        PublicationResult result = transactions.execute(() -> request == null || request.effectiveFrom() == null
                ? publishSchema.publish(SchemaId.of(id))
                : publishSchema.publish(SchemaId.of(id), request.effectiveFrom()));
        if (!result.published()) {
            throw new PublicationRefusedException(result.violations());
        }
        VersionDto version = VersionDto.of(result.publishedVersion());
        return ResponseEntity.created(URI.create("/api/schemas/" + id + "/versions/" + version.number()))
                .body(version);
    }

    @PostMapping("/{id}/applicability")
    SchemaResponse applyTo(@PathVariable("id") String id, @RequestBody ApplicabilityRequest request) {
        existing(id);
        return view(transactions.execute(() -> applicability.applyTo(SchemaId.of(id), request.assetType())));
    }

    @DeleteMapping("/{id}/applicability/{assetType}")
    SchemaResponse stopApplyingTo(@PathVariable("id") String id, @PathVariable("assetType") AssetType assetType) {
        existing(id);
        return view(transactions.execute(() -> applicability.stopApplyingTo(SchemaId.of(id), assetType)));
    }

    /** Moves an asset type from this schema to another one atomically. */
    @PostMapping("/{id}/applicability/transfer")
    SchemaResponse transfer(@PathVariable("id") String id, @RequestBody TransferRequest request) {
        existing(id);
        existing(request.targetSchemaId());
        return view(transactions.execute(() -> applicability.transferTo(SchemaId.of(id),
                SchemaId.of(request.targetSchemaId()), request.assetType())));
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidArgumentException("'at' must be an ISO-8601 instant such as 2030-01-01T00:00:00Z");
        }
    }

    private InspectionSchema existing(String id) {
        return schemas.find(SchemaId.of(id))
                .orElseThrow(() -> new NotFoundException("schema " + id + " does not exist"));
    }

    private SchemaResponse view(InspectionSchema schema) {
        return SchemaResponse.of(schema, clock.now());
    }
}
