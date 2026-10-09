package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.ApplicabilityRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.CreateSchemaRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.PublishRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.SchemaResponse;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.SectionDto;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.TransferRequest;
import ar.edu.itba.dps.certification.app.web.dto.SchemaDtos.VersionDto;
import ar.edu.itba.dps.certification.application.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.application.schema.usecase.ChangeSchemaApplicability;
import ar.edu.itba.dps.certification.application.schema.usecase.CreateSchema;
import ar.edu.itba.dps.certification.application.schema.usecase.DiscardDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.EditDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.OpenDraft;
import ar.edu.itba.dps.certification.application.schema.usecase.PublishSchemaVersion;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.InspectionSchema;
import ar.edu.itba.dps.certification.domain.schema.PublicationResult;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.infrastructure.persistence.jdbc.JdbcTransactions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
    private final SchemaRepository schemas;
    private final Clock clock;
    private final JdbcTransactions transactions;

    SchemaController(CreateSchema createSchema, OpenDraft openDraft, EditDraft editDraft,
            DiscardDraft discardDraft, PublishSchemaVersion publishSchema,
            ChangeSchemaApplicability applicability, SchemaRepository schemas, Clock clock,
            JdbcTransactions transactions) {
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
        return schemas.findAll().stream().map(this::view).toList();
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

    private InspectionSchema existing(String id) {
        return schemas.findById(SchemaId.of(id))
                .orElseThrow(() -> new NotFoundException("schema " + id + " does not exist"));
    }

    private SchemaResponse view(InspectionSchema schema) {
        return SchemaResponse.of(schema, clock.now());
    }
}
