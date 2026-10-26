package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.InspectionNote;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class RemoveNote implements ar.edu.itba.dps.certification.domain.inspection.port.inbound.RemoveNote {
    private final Transactions transactions;

    private final InspectionRepository inspections;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public RemoveNote(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.inspections = inspections;
        this.audit = audit;
        this.actors = actors;
    }

    public Inspection remove(InspectionId inspectionId, String noteId) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Inspection inspection = inspections.require(inspectionId);
            InspectionNote removed = inspection.removeNote(actor.partyId(), noteId);
            inspections.save(inspection);
            audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                    AuditAction.INSPECTION_DATA_CORRECTED,
                    AuditDetail.dataChanged(new FieldChange("note." + noteId, removed.text(), null)));
            return inspection;
        });
    }
}
