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

public final class RemoveNote {

    private final InspectionRepository inspections;
    private final AuditRecorder audit;

    public RemoveNote(InspectionRepository inspections, AuditRecorder audit) {
        this.inspections = inspections;
        this.audit = audit;
    }

    public Inspection remove(InspectionId inspectionId, String noteId) {
        Inspection inspection = inspections.require(inspectionId);
        InspectionNote removed = inspection.removeNote(noteId);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("note." + noteId, removed.text(), null)));
        return inspection;
    }
}
