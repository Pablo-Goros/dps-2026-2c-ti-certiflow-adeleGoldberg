package ar.edu.itba.dps.certification.application.inspection.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;

public interface FindingRegistry {

    void recordClosureNonConformities(InspectionId inspectionId, AssetId assetId,
            PartyId responsibleAtClose, List<NonConformity> nonConformities);

    void registerRevealedNonConformity(InspectionId inspectionId, AssetId assetId,
            PartyId responsible, NonConformity nonConformity, RectificationId rectificationId);

    void reviseNonConformity(InspectionId inspectionId, NonConformity nonConformity,
            RectificationId rectificationId, String reason);

    void correctPresentedEvidence(InspectionId inspectionId, CriterionId criterionId,
            List<String> presentedEvidence, RectificationId rectificationId, String reason);

    void voidObligation(InspectionId inspectionId, CriterionId criterionId,
            RectificationId rectificationId, String reason);
}
