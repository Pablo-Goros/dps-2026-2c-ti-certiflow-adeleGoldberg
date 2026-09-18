package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

public final class CorrectNote {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;

    public CorrectNote(InspectionRepository inspections, AuditRecorder audit) {
        this.inspections = inspections;
        this.audit = audit;
    }

    public InspectionNote correct(InspectionId inspectionId, String noteId, String text) {
        Inspection inspection = inspections.require(inspectionId);
        InspectionNote previous = inspection.correctNote(noteId, text);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("note." + noteId, previous.text(), text)));
        return inspection.requireNote(noteId);
    }
}
