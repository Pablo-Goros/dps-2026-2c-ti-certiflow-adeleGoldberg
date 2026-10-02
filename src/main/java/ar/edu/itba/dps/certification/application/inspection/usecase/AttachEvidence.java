package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.application.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;

public final class AttachEvidence {

    private final InspectionRepository inspections;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public AttachEvidence(InspectionRepository inspections, IdGenerator ids, Clock clock,
            AuditRecorder audit, ActorProvider actors) {
        this.inspections = inspections;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public EvidenceRecord attach(InspectionId inspectionId, CriterionId criterionId, String requirementLabel,
            String reference) {
        var actor = actors.requireUser();
        Inspection inspection = inspections.require(inspectionId);
        EvidenceRecord record = inspection.attachEvidence(actor.partyId(), criterionId, requirementLabel,
                ids.newIdentifier(), reference, clock.now());
        inspections.save(inspection);
        audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_RECORDED,
                AuditDetail.dataChanged(new FieldChange(
                        "evidence." + criterionId + "." + record.id(), null, record.reference())));
        return record;
    }
}
