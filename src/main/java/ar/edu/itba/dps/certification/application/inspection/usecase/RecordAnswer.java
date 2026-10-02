package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

public final class RecordAnswer {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;

    public RecordAnswer(InspectionRepository inspections, AuditRecorder audit) {
        this.inspections = inspections;
        this.audit = audit;
    }

    public Inspection record(InspectionId inspectionId, CriterionId criterionId, Answer answer) {
        Inspection inspection = inspections.require(inspectionId);
        String previous = inspection.requireRecord(criterionId).answer()
                .map(Answer::describe).orElse(null);
        inspection.recordAnswer(criterionId, answer);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                previous == null ? AuditAction.INSPECTION_DATA_RECORDED
                        : AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("answer." + criterionId, previous,
                        answer.describe())));
        return inspection;
    }
}
