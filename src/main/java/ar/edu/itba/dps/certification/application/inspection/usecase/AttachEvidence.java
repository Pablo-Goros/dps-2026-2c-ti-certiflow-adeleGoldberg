package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;

public final class AttachEvidence {

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;

    public AttachEvidence(InspectionRepository inspections, SchemaCatalog schemas, IdGenerator ids,
            Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.schemas = schemas;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public EvidenceRecord attach(InspectionId inspectionId, CriterionId criterionId, String requirementLabel,
            String reference) {
        Inspection inspection = inspections.require(inspectionId);
        SchemaVersion version = schemas.requireVersion(inspection.requireFrozenSchemaVersionId());
        Criterion criterion = version.requireCriterion(criterionId);
        EvidenceRequirement requirement = criterion.evidenceRequirements().stream()
                .filter(declared -> declared.label().equals(requirementLabel))
                .findFirst()
                .orElseThrow(() -> new DomainException("criterion " + criterionId
                        + " declares no evidence requirement labelled '" + requirementLabel + "'"));
        EvidenceRecord record = new EvidenceRecord(ids.newIdentifier(), requirement.label(),
                requirement.type(), reference, clock.now());
        inspection.attachEvidence(criterionId, record);
        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_DATA_RECORDED,
                AuditDetail.dataChanged(new FieldChange(
                        "evidence." + criterionId + "." + record.id(), null, record.reference())));
        return record;
    }
}
