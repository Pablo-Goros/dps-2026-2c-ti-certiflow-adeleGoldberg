package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.time.Instant;

public final class StartInspection implements ar.edu.itba.dps.certification.domain.inspection.port.inbound.StartInspection {
    private final Transactions transactions;

    private final InspectionRepository inspections;
    private final AssetDirectory assets;
    private final SchemaCatalog schemas;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public StartInspection(InspectionRepository inspections, AssetDirectory assets,
            SchemaCatalog schemas, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.inspections = inspections;
        this.assets = assets;
        this.schemas = schemas;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public Inspection start(InspectionId inspectionId) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Inspection inspection = inspections.require(inspectionId);
            AssetType assetType = assets.assetTypeOf(inspection.assetId());
            Instant now = clock.now();
            SchemaVersion version = schemas.effectiveVersionFor(assetType, now)
                    .orElseThrow(() -> new DomainException("asset type " + assetType
                            + " has no published schema version effective at " + now + ", so the inspection cannot start"));
            AssetSnapshot snapshot = assets.captureSnapshot(inspection.assetId());
            inspection.start(actor.partyId(), version, snapshot, now);
            inspections.save(inspection);
            audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                    AuditAction.INSPECTION_STARTED, AuditDetail.stateChanged("ASSIGNED", "IN_PROGRESS"));
            return inspection;
        });
    }
}
