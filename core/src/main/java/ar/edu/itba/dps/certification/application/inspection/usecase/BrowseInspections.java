package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionStatus;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

/** Read side of the inspections: lookup, filtering and the schema version each one froze. */
public final class BrowseInspections {

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;

    public BrowseInspections(InspectionRepository inspections, SchemaCatalog schemas) {
        this.inspections = inspections;
        this.schemas = schemas;
    }

    public Optional<Inspection> find(InspectionId id) {
        return inspections.findById(id);
    }

    /** Every filter is optional; the ones given must all match. */
    public List<Inspection> search(Optional<AssetId> asset, Optional<PartyId> inspector,
            Optional<InspectionStatus> status) {
        return inspections.findAll().stream()
                .filter(inspection -> asset.map(inspection.assetId()::equals).orElse(true))
                .filter(inspection -> inspector.map(inspection.inspector()::equals).orElse(true))
                .filter(inspection -> status.map(wanted -> inspection.status() == wanted).orElse(true))
                .toList();
    }

    /** The schema version the inspection froze when it started; empty before that. */
    public Optional<SchemaVersion> frozenVersion(Inspection inspection) {
        return inspection.frozenSchemaVersionId().map(schemas::requireVersion);
    }
}
