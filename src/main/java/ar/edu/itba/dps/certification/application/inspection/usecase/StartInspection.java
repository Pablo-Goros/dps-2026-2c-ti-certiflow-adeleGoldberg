package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.DomainException;

public final class StartInspection {

    private final InspectionRepository inspections;
    private final AssetDirectory assets;
    private final SchemaCatalog schemas;
    private final Clock clock;
    private final AuditRecorder audit;

    public StartInspection(InspectionRepository inspections, AssetDirectory assets,
            SchemaCatalog schemas, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.assets = assets;
        this.schemas = schemas;
        this.clock = clock;
        this.audit = audit;
    }

    public Inspection start(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        AssetType assetType = assets.assetTypeOf(inspection.assetId());
        SchemaVersion version = schemas.latestPublishedVersionFor(assetType)
                .orElseThrow(() -> new DomainException("asset type " + assetType
                        + " has no published schema version, so the inspection cannot start"));
        AssetSnapshot snapshot = assets.captureSnapshot(inspection.assetId());
        inspection.start(version, snapshot, clock.now());
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_STARTED, AuditDetail.stateChanged("ASSIGNED", "IN_PROGRESS"));
        return inspection;
    }
}
