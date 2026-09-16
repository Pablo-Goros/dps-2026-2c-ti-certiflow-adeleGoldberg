package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.LocalDate;

public final class AssignInspection {

    private final InspectionRepository inspections;
    private final AssetDirectory assets;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public AssignInspection(InspectionRepository inspections, AssetDirectory assets, IdGenerator ids, AuditRecorder audit) {
        this.inspections = inspections;
        this.assets = assets;
        this.ids = ids;
        this.audit = audit;
    }

    public Inspection assign(AssetId assetId, PartyId inspector, LocalDate expectedDate) {
        assets.assetTypeOf(assetId);
        inspections.findNonClosedByAsset(assetId).ifPresent(open -> {
            throw new DomainException("asset " + assetId + " already has inspection " + open.id()
                    + " in progress");
        });
        Inspection inspection =
                new Inspection(new InspectionId(ids.newIdentifier()), assetId, inspector, expectedDate);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_ASSIGNED,
                AuditDetail.created("inspection of asset " + assetId + " assigned to " + inspector
                        + ", expected " + expectedDate));
        return inspection;
    }
}
