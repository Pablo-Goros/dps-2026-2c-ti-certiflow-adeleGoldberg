package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.answer.Answer;

public final class RecordAnswer {

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;
    private final AuditRecorder audit;

    public RecordAnswer(InspectionRepository inspections, SchemaCatalog schemas, AuditRecorder audit) {
        this.inspections = inspections;
        this.schemas = schemas;
        this.audit = audit;
    }

    public Inspection record(InspectionId inspectionId, CriterionId criterionId, Answer answer) {
        Inspection inspection = inspections.require(inspectionId);
        SchemaVersion version = schemas.requireVersion(inspection.requireFrozenSchemaVersionId());
        Criterion criterion = version.requireCriterion(criterionId);
        criterion.rule().admissibilityViolation(answer).ifPresent(violation -> {
            throw new DomainException("answer refused for criterion " + criterionId + ": " + violation);
        });
        String previous = inspection.requireRecord(criterionId).answer()
                .map(Answer::describe).orElse(null);
        inspection.recordAnswer(criterionId, answer);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                previous == null ? AuditAction.INSPECTION_DATA_RECORDED
                        : AuditAction.INSPECTION_DATA_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("answer." + criterionId, previous,
                        answer.describe())));
        return inspection;
    }
}
