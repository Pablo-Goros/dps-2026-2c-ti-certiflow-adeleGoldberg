package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionClosureResult;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;

import java.time.Instant;
import java.util.List;

public final class CloseInspection {

    private final InspectionRepository inspections;
    private final AssetDirectory assets;
    private final FindingRegistry findings;
    private final Clock clock;
    private final AuditRecorder audit;

    public CloseInspection(InspectionRepository inspections,
            AssetDirectory assets, FindingRegistry findings, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.assets = assets;
        this.findings = findings;
        this.clock = clock;
        this.audit = audit;
    }

    public InspectionClosureResult close(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        if (inspection.status().closed()) {
            return new InspectionClosureResult(inspection.id(), inspection.closedAt().orElseThrow(),
                    true, inspection.currentEvaluations());
        }

        Instant closedAt = clock.now();
        InspectionClosureResult result = inspection.close(closedAt);
        inspections.save(inspection);

        PartyId responsibleAtClose = assets.currentResponsible(inspection.assetId());
        List<NonConformity> nonConformities = result.nonApproved().entrySet().stream()
                .map(entry -> new NonConformity(entry.getKey(), entry.getValue(),
                        evidenceReferencesOf(inspection, entry.getKey())))
                .toList();
        findings.recordClosureNonConformities(inspection.id(), inspection.assetId(), responsibleAtClose, inspection.inspector(),
                nonConformities);

        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_CLOSED,
                AuditDetail.decision("close the inspection", result.nonApproved().size() + " of "
                        + result.evaluations().size() + " criteria not approved"));
        return result;
    }

    private List<String> evidenceReferencesOf(Inspection inspection, CriterionId criterionId) {
        return inspection.requireRecord(criterionId).evidence().stream()
                .map(EvidenceRecord::reference)
                .toList();
    }
}
