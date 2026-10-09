package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;

public final class CorrectNote {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public CorrectNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors) {
        this.inspections = inspections;
        this.audit = audit;
        this.actors = actors;
    }

    public InspectionNote correct(InspectionId inspectionId, String noteId, String text) {
        var actor = actors.requireUser();
        Inspection inspection = inspections.require(inspectionId);
        InspectionNote previous = inspection.correctNote(actor.partyId(), noteId, text);
        inspections.save(inspection);
        InspectionNote corrected = inspection.requireNote(noteId);
        audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("note." + noteId, previous.text(), corrected.text())));
        return corrected;
    }
}
