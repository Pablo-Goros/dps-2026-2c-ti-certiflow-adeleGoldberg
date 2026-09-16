package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public final class RecordNote {

    private final InspectionRepository inspections;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;

    public RecordNote(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public InspectionNote record(InspectionId inspectionId, Optional<CriterionId> criterionId, String text) {
        Validate.required(criterionId, "criterion id");
        Inspection inspection = inspections.require(inspectionId);
        InspectionNote note = new InspectionNote(ids.newIdentifier(), criterionId, text,
                inspection.inspector(), clock.now());
        inspection.recordNote(note);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_RECORDED,
                AuditDetail.dataChanged(new FieldChange("note." + note.id(), null, note.text())));
        return note;
    }
}
