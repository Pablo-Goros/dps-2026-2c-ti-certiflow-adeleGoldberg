package ar.edu.itba.dps.certification.domain.inspection.port;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;

public interface FindingRegistry {

    void recordClosureNonConformities(InspectionId inspectionId, AssetId assetId,
            PartyId responsibleAtClose, PartyId inspector, List<NonConformity> nonConformities);

    void registerRevealedNonConformity(InspectionId inspectionId, AssetId assetId,
            PartyId responsible, PartyId inspector, NonConformity nonConformity, RectificationId rectificationId);

    void reviseNonConformity(InspectionId inspectionId, NonConformity nonConformity,
            RectificationId rectificationId, String reason);

    void correctPresentedEvidence(InspectionId inspectionId, CriterionId criterionId,
            List<String> presentedEvidence, RectificationId rectificationId, String reason);

    void voidObligation(InspectionId inspectionId, CriterionId criterionId,
            RectificationId rectificationId, String reason);

    default List<DomainEvent> pendingEvents(InspectionId inspectionId) {
        return List.of();
    }

    default void acknowledgeEvent(InspectionId inspectionId,
            DomainEvent event) { }
}
