package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.CriterionEvaluation;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.ArrayList;
import java.util.List;

public final class RecordingFindingRegistry implements FindingRegistry {

    public record ClosureCall(InspectionId inspectionId, AssetId assetId, PartyId responsible,
            List<NonConformity> nonConformities) {
    }

    public record RevealedCall(InspectionId inspectionId, CriterionId criterionId, PartyId responsible,
            RectificationId rectificationId) {
    }

    public record RevisedCall(InspectionId inspectionId, CriterionId criterionId,
            CriterionEvaluation evaluation, RectificationId rectificationId) {
    }

    public record EvidenceCall(InspectionId inspectionId, CriterionId criterionId,
            List<String> presentedEvidence, RectificationId rectificationId) {
    }

    public record VoidedCall(InspectionId inspectionId, CriterionId criterionId,
            RectificationId rectificationId) {
    }

    public final List<ClosureCall> closures = new ArrayList<>();
    public final List<RevealedCall> revealed = new ArrayList<>();
    public final List<RevisedCall> revised = new ArrayList<>();
    public final List<EvidenceCall> evidenceCorrections = new ArrayList<>();
    public final List<VoidedCall> voided = new ArrayList<>();

    @Override
    public void recordClosureNonConformities(InspectionId inspectionId, AssetId assetId,
            PartyId responsibleAtClose, List<NonConformity> nonConformities) {
        closures.add(new ClosureCall(inspectionId, assetId, responsibleAtClose, nonConformities));
    }

    @Override
    public void registerRevealedNonConformity(InspectionId inspectionId, AssetId assetId,
            PartyId responsible, NonConformity nonConformity, RectificationId rectificationId) {
        revealed.add(new RevealedCall(inspectionId, nonConformity.criterionId(), responsible,
                rectificationId));
    }

    @Override
    public void reviseNonConformity(InspectionId inspectionId, NonConformity nonConformity,
            RectificationId rectificationId, String reason) {
        revised.add(new RevisedCall(inspectionId, nonConformity.criterionId(), nonConformity.evaluation(),
                rectificationId));
    }

    @Override
    public void correctPresentedEvidence(InspectionId inspectionId, CriterionId criterionId,
            List<String> presentedEvidence, RectificationId rectificationId, String reason) {
        evidenceCorrections.add(new EvidenceCall(inspectionId, criterionId, presentedEvidence,
                rectificationId));
    }

    @Override
    public void voidObligation(InspectionId inspectionId, CriterionId criterionId,
            RectificationId rectificationId, String reason) {
        voided.add(new VoidedCall(inspectionId, criterionId, rectificationId));
    }
}
