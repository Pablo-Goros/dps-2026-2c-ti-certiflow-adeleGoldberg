package ar.edu.itba.dps.certification.application.inspection.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.application.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.evaluation.CriterionEvaluator;
import ar.edu.itba.dps.certification.domain.inspection.CriterionResultRevised;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Correction;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.Criterion;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersion;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.Instant;
import java.util.List;

public final class RectifyClosedInspection {

    private final InspectionRepository inspections;
    private final SchemaCatalog schemas;
    private final AssetDirectory assets;
    private final CriterionEvaluator evaluator;
    private final FindingRegistry findings;
    private final DomainEventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;

    public RectifyClosedInspection(InspectionRepository inspections, SchemaCatalog schemas,
            AssetDirectory assets, CriterionEvaluator evaluator, FindingRegistry findings,
            DomainEventPublisher events, IdGenerator ids, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.schemas = schemas;
        this.assets = assets;
        this.evaluator = evaluator;
        this.findings = findings;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public Rectification rectify(InspectionId inspectionId, PartyId author, String reason,
            List<Correction> corrections) {
        Inspection inspection = inspections.require(inspectionId);
        Instant at = clock.now();
        RectificationId rectificationId = new RectificationId(ids.newIdentifier());
        SchemaVersion originalVersion =
                schemas.requireVersion(inspection.requireFrozenSchemaVersionId());
        Rectification rectification = inspection.rectify(originalVersion, rectificationId, author,
                at, reason, corrections);
        for (CriterionId criterionId : rectification.affectedCriteria()) {
            reevaluate(inspection, originalVersion, criterionId, rectification, at);
        }

        inspections.save(inspection);
        audit.record(AuditedElementRef.inspection(inspection.id().value()),
                AuditAction.INSPECTION_RECTIFIED,
                AuditDetail.dataChanged(rectification.changes().stream()
                        .map(change -> new FieldChange(
                                change.field(),
                                change.previousValue(),
                                change.currentValue()))
                        .toList()),
                rectification.reason());
        return rectification;
    }

    private void reevaluate(Inspection inspection, SchemaVersion originalVersion, CriterionId criterionId,
            Rectification rectification, Instant at) {
        Criterion criterion = originalVersion.requireCriterion(criterionId);
        CriterionEvaluation previous = inspection.requireRecord(criterionId).currentEvaluation().orElseThrow();
        CriterionEvaluation recomputed = evaluator.evaluate(criterion, inspection.requireRecord(criterionId), at)
                .asRectificationOf(rectification.id(), at);

        if (unchanged(previous, recomputed)) {
            findings.correctPresentedEvidence(inspection.id(), criterionId,
                    presentedEvidence(inspection, criterionId),
                    rectification.id(), rectification.reason());
            return;
        }
        inspection.recordEvaluationProducedBy(rectification, criterionId, recomputed);

        if (!previous.result().approved() && recomputed.result().approved()) {
            findings.voidObligation(inspection.id(), criterionId, rectification.id(), rectification.reason());
        } else if (previous.result().approved() && !recomputed.result().approved()) {
            findings.registerRevealedNonConformity(inspection.id(), inspection.assetId(),
                    assets.currentResponsible(inspection.assetId()),
                    nonConformity(inspection, criterionId, recomputed), rectification.id());
        } else {
            findings.reviseNonConformity(inspection.id(), nonConformity(inspection, criterionId, recomputed),
                    rectification.id(), rectification.reason());
        }

        if (previous.result() != recomputed.result()) {
            events.publish(new CriterionResultRevised(inspection.id(), criterionId, previous.result(),
                    recomputed.result(), rectification.id(), rectification.reason(), at));
        }
    }

    private NonConformity nonConformity(Inspection inspection, CriterionId criterionId,
            CriterionEvaluation evaluation) {
        return new NonConformity(criterionId, evaluation, presentedEvidence(inspection, criterionId));
    }

    private List<String> presentedEvidence(Inspection inspection, CriterionId criterionId) {
        return inspection.requireRecord(criterionId).evidence().stream()
                .map(EvidenceRecord::reference)
                .toList();
    }

    private boolean unchanged(CriterionEvaluation previous, CriterionEvaluation recomputed) {
        return previous.result() == recomputed.result()
                && previous.reasons().equals(recomputed.reasons());
    }
}
