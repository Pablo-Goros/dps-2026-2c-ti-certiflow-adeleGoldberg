package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.record.ObservedNonConformity;
import ar.edu.itba.dps.certification.domain.inspection.record.SuggestionOrigin;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.Optional;

public final class RecordObservedNonConformity implements ar.edu.itba.dps.certification.domain.inspection.port.inbound.RecordObservedNonConformity {
    private final Transactions transactions;

    private final InspectionRepository inspections;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;
    private final ActorProvider actors;

    public RecordObservedNonConformity(InspectionRepository inspections, IdGenerator ids, Clock clock,
            AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        this.transactions = transactions;
        this.inspections = inspections;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.actors = actors;
    }

    public ObservedNonConformity record(InspectionId inspectionId, CriterionId criterionId, CriterionResult result,
            Severity severity, String description, Optional<String> noteId) {
        return recorded(inspectionId, criterionId, result, severity, description, noteId, Optional.empty());
    }

    public ObservedNonConformity recordSuggested(InspectionId inspectionId, CriterionId criterionId,
            CriterionResult result, Severity severity, String description, SuggestionOrigin origin) {
        return recorded(inspectionId, criterionId, result, severity, description, Optional.empty(),
                Optional.of(origin));
    }

    private ObservedNonConformity recorded(InspectionId inspectionId, CriterionId criterionId,
            CriterionResult result, Severity severity, String description, Optional<String> noteId,
            Optional<SuggestionOrigin> origin) {
        return transactions.execute(() -> {
            var actor = actors.requireUser();
            Inspection inspection = inspections.require(inspectionId);
            ObservedNonConformity observation = inspection.recordObservedNonConformity(actor.partyId(),
                    criterionId, ids.newIdentifier(), result, severity, description, noteId, clock.now(), origin);
            inspections.save(inspection);
            String recordedValue = observation.describe()
                    + origin.map(suggested -> " [" + suggested.describe() + "]").orElse("");
            audit.recordAs(actor, AuditedElementRef.inspection(inspection.id().value()),
                    AuditAction.INSPECTION_DATA_RECORDED,
                    AuditDetail.dataChanged(new FieldChange("observation." + criterionId + "." + observation.id(),
                            null, recordedValue)));
            return observation;
        });
    }
}
