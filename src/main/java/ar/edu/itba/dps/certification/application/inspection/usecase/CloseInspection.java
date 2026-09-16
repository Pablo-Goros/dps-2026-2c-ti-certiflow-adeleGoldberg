package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionClosureResult;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CloseInspection {

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;
    private final AssetDirectory assets;
    private final CriterionEvaluator evaluator;
    private final FindingRegistry findings;
    private final Clock clock;
    private final AuditRecorder audit;

    public CloseInspection(InspectionRepository inspections, SchemaCatalog schemas,
            AssetDirectory assets, CriterionEvaluator evaluator, FindingRegistry findings, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.schemas = schemas;
        this.assets = assets;
        this.evaluator = evaluator;
        this.findings = findings;
        this.clock = clock;
        this.audit = audit;
    }

    public InspectionClosureResult close(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        if (inspection.status().closed()) {
            return new InspectionClosureResult(inspection.id(), inspection.closedAt().orElseThrow(),
                    true, inspection.currentEvaluations());
        }

        Instant closedAt = clock.now();
        SchemaVersion version = schemas.requireVersion(inspection.requireFrozenSchemaVersionId());
        Map<CriterionId, CriterionEvaluation> evaluations = new LinkedHashMap<>();
        for (Criterion criterion : version.criteria()) {
            evaluations.put(criterion.id(), evaluator.evaluate(criterion,
                    inspection.requireRecord(criterion.id()), version, closedAt));
        }

        InspectionClosureResult result = inspection.close(closedAt, evaluations);
        inspections.save(inspection);

        PartyId responsibleAtClose = assets.currentResponsible(inspection.assetId());
        List<NonConformity> nonConformities = result.nonApproved().entrySet().stream()
                .map(entry -> new NonConformity(entry.getKey(), entry.getValue(),
                        evidenceReferencesOf(inspection, entry.getKey())))
                .toList();
        findings.recordClosureNonConformities(inspection.id(), inspection.assetId(), responsibleAtClose,
                nonConformities);

        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_CLOSED,
                AuditDetail.decision("close the inspection", result.nonApproved().size() + " of "
                        + result.evaluations().size() + " criteria not approved"));
        return result;
    }

    private List<String> evidenceReferencesOf(Inspection inspection, CriterionId criterionId) {
        return inspection.requireRecord(criterionId).evidence().stream()
                .map(EvidenceRecord::reference)
                .toList();
    }
}
