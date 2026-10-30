package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class WithdrawObservedNonConformity implements ar.edu.itba.dps.certification.domain.inspection.port.inbound.WithdrawObservedNonConformity {
    private final Transactions transactions;

    private final InspectionRepository inspections;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public WithdrawObservedNonConformity(InspectionRepository inspections, AuditRecorder audit, ActorProvider actors,
            Transactions transactions) {
        this.transactions = transactions;
        this.inspections = inspections;
        this.audit = audit;
        this.actors = actors;
    }

    public Inspection withdraw(InspectionId inspectionId, CriterionId criterionId, String observationId) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Inspection inspection = inspections.require(inspectionId);
            ObservedNonConformity withdrawn = inspection.withdrawObservedNonConformity(actor.partyId(),
                    criterionId, observationId);
            inspections.save(inspection);
            audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                    AuditAction.INSPECTION_DATA_CORRECTED,
                    AuditDetail.dataChanged(new FieldChange("observation." + criterionId + "." + observationId,
                            withdrawn.describe(), null)));
            return inspection;
        });
    }
}
