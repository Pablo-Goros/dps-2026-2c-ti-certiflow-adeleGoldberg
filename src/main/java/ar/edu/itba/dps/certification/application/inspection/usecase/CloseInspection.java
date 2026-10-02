package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionClosureResult;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import java.util.List;

/**
 * Closes the inspection and makes sure every non-approved criterion has its finding.
 *
 * <p>Registering non-conformities is idempotent, so a repeated request completes a closure that
 * was saved but whose findings were never registered (for example, a failure between both steps)
 * instead of leaving the inspection closed with no findings and impossible to certify.
 */
public final class CloseInspection {

    private final InspectionRepository inspections;
    private final AssetDirectory assets;
    private final FindingRegistry findings;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public CloseInspection(InspectionRepository inspections, AssetDirectory assets, FindingRegistry findings,
            Clock clock, AuditRecorder audit, ActorProvider actors) {
        this.inspections = inspections;
        this.assets = assets;
        this.findings = findings;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public InspectionClosureResult close(InspectionId inspectionId) {
        var actor = actors.requireUser();
        Inspection inspection = inspections.require(inspectionId);
        InspectionClosureResult result = inspection.close(actor.partyId(), clock.now());
        if (!result.alreadyClosed()) {
            inspections.save(inspection);
            audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                    AuditAction.INSPECTION_CLOSED,
                    AuditDetail.decision("close the inspection", result.nonApproved().size() + " of "
                            + result.evaluations().size() + " criteria not approved"));
        }
        List<NonConformity> nonConformities = result.nonApproved().entrySet().stream()
                .map(entry -> new NonConformity(entry.getKey(), entry.getValue(),
                        evidenceReferencesOf(inspection, entry.getKey())))
                .toList();
        findings.recordClosureNonConformities(inspection.id(), inspection.assetId(),
                assets.currentResponsible(inspection.assetId()), inspection.inspector(), nonConformities);
        return result;
    }

    private List<String> evidenceReferencesOf(Inspection inspection, CriterionId criterionId) {
        return inspection.requireRecord(criterionId).evidence().stream()
                .map(EvidenceRecord::reference)
                .toList();
    }
}
