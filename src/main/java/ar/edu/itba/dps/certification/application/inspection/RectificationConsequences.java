package ar.edu.itba.dps.certification.application.inspection;

import ar.edu.itba.dps.certification.application.catalogue.port.AssetDirectory;
import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.record.EvidenceRecord;
import ar.edu.itba.dps.certification.domain.inspection.rectification.Rectification;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;

import java.util.Map;

public final class RectificationConsequences {
    private final FindingRegistry findings;
    private final AssetDirectory assets;

    public RectificationConsequences(FindingRegistry findings, AssetDirectory assets) {
        this.findings = findings;
        this.assets = assets;
    }

    public void apply(Inspection inspection, Rectification rectification,
            Map<CriterionId, CriterionEvaluation> previousEvaluations) {
        for (CriterionId criterionId : rectification.affectedCriteria()) {
            CriterionEvaluation previous = previousEvaluations.get(criterionId);
            CriterionEvaluation current = inspection.currentEvaluations().get(criterionId);
            var evidence = inspection.requireRecord(criterionId).evidence().stream()
                    .map(EvidenceRecord::reference).toList();
            if (previous.result() == current.result() && previous.reasons().equals(current.reasons())) {
                findings.correctPresentedEvidence(inspection.id(), criterionId, evidence,
                        rectification.id(), rectification.reason());
            } else if (current.result().approved()) {
                findings.voidObligation(inspection.id(), criterionId, rectification.id(), rectification.reason());
            } else if (previous.result().approved()) {
                findings.registerRevealedNonConformity(inspection.id(), inspection.assetId(),
                        assets.currentResponsible(inspection.assetId()), inspection.inspector(),
                        new NonConformity(criterionId, current, evidence), rectification.id());
            } else {
                findings.reviseNonConformity(inspection.id(), new NonConformity(criterionId, current, evidence),
                        rectification.id(), rectification.reason());
            }
        }
    }
}
