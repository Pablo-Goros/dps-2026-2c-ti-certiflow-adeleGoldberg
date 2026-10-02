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
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;

public final class RemoveAnswer {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public RemoveAnswer(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        this.inspections = inspections;
        this.audit = audit;
        this.actors = actors;
    }

    public Inspection remove(InspectionId inspectionId, CriterionId criterionId) {
        var actor = actors.requireUser();
        Inspection inspection = inspections.require(inspectionId);
        String previous = inspection.requireRecord(criterionId).answer()
                .map(Answer::describe).orElse(null);
        inspection.removeAnswer(actor.partyId(), criterionId);
        inspections.save(inspection);
        audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("answer." + criterionId, previous, null)));
        return inspection;
    }
}
