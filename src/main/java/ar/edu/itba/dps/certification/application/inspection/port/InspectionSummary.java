package ar.edu.itba.dps.certification.application.inspection.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public record InspectionSummary(
        InspectionId id,
        AssetId assetId,
        PartyId inspector,
        InspectionStatus status,
        LocalDate expectedDate,
        Optional<Instant> startedAt,
        Optional<Instant> closedAt,
        Optional<SchemaVersionId> schemaVersionId,
        Optional<AssetSnapshot> asset) {

    public InspectionSummary {
        Validate.required(id, "inspection id");
        Validate.required(assetId, "asset id");
        Validate.required(inspector, "inspector");
        Validate.required(status, "status");
        Validate.required(expectedDate, "expected date");
        Validate.required(startedAt, "start instant");
        Validate.required(closedAt, "closure instant");
        Validate.required(schemaVersionId, "schema version id");
        Validate.required(asset, "asset snapshot");
    }

    public static InspectionSummary of(Inspection inspection) {
        Validate.required(inspection, "inspection");
        return new InspectionSummary(
                inspection.id(),
                inspection.assetId(),
                inspection.inspector(),
                inspection.status(),
                inspection.expectedDate(),
                inspection.startedAt(),
                inspection.closedAt(),
                inspection.frozenSchemaVersionId(),
                inspection.assetSnapshot());
    }

    public boolean closed() {
        return status.closed();
    }

    public SchemaVersionId requireSchemaVersionId() {
        return schemaVersionId.orElseThrow(() ->
                new DomainException("inspection " + id + " has not been started"));
    }
}
