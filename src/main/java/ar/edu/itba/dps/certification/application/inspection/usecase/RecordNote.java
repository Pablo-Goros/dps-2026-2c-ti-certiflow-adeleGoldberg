package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import java.util.Optional;

public final class RecordNote {

    private final InspectionRepository inspections;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public RecordNote(InspectionRepository inspections, IdGenerator ids, Clock clock, AuditRecorder audit,
            ActorProvider actors) {
        this.inspections = inspections;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public InspectionNote record(InspectionId inspectionId, Optional<CriterionId> criterionId, String text) {
        Validate.required(criterionId, "criterion id");
        var actor = actors.requireUser();
        Inspection inspection = inspections.require(inspectionId);
        InspectionNote note = inspection.recordNote(actor.partyId(), ids.newIdentifier(), criterionId, text,
                clock.now());
        inspections.save(inspection);
        audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_RECORDED,
                AuditDetail.dataChanged(new FieldChange("note." + note.id(), null, note.text())));
        return note;
    }
}
