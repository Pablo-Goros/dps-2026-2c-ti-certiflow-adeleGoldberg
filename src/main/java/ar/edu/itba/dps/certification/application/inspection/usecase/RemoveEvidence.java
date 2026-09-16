package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

public final class RemoveEvidence {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;

    public RemoveEvidence(InspectionRepository inspections, AuditRecorder audit) {
        this.inspections = inspections;
        this.audit = audit;
    }

    public Inspection remove(InspectionId inspectionId, CriterionId criterionId, String evidenceId) {
        Inspection inspection = inspections.require(inspectionId);
        EvidenceRecord removed = inspection.removeEvidence(criterionId, evidenceId);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange(
                        "evidence." + criterionId + "." + evidenceId, removed.reference(), null)));
        return inspection;
    }
}
