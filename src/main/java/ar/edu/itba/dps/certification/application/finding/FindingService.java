package ar.edu.itba.dps.certification.application.finding;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.finding.port.FindingRepository;
import ar.edu.itba.dps.certification.application.inspection.port.FindingRegistry;
import ar.edu.itba.dps.certification.application.inspection.port.NonConformity;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.DomainEventPublisher;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.Finding;
import ar.edu.itba.dps.certification.domain.finding.FindingId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class FindingService implements FindingRegistry {

    private final FindingRepository findings;
    private final IdGenerator ids;
    private final Clock clock;
    private final DomainEventPublisher events;
    private final AuditRecorder audit;

    public FindingService(FindingRepository findings, IdGenerator ids, Clock clock, DomainEventPublisher events, AuditRecorder audit) {
        this.findings = findings;
        this.ids = ids;
        this.clock = clock;
        this.events = events;
        this.audit = audit;
    }

    @Override
    public void recordClosureNonConformities(InspectionId inspectionId, AssetId assetId,
            PartyId responsibleAtClose, List<NonConformity> nonConformities) {
        for (NonConformity nonConformity : nonConformities) {
            if (findings.findByCriterion(inspectionId, nonConformity.criterionId()).isPresent()) {
                continue;
            }
            create(inspectionId, assetId, responsibleAtClose, nonConformity);
        }
    }

    @Override
    public void registerRevealedNonConformity(InspectionId inspectionId, AssetId assetId,
            PartyId responsible, NonConformity nonConformity, RectificationId rectificationId) {
        if (findings.findByCriterion(inspectionId, nonConformity.criterionId()).isPresent()) {
            reviseNonConformity(inspectionId, nonConformity, rectificationId,
                    "non-conformity revealed by a rectification");
            return;
        }
        create(inspectionId, assetId, responsible, nonConformity);
    }

    @Override
    public void reviseNonConformity(InspectionId inspectionId, NonConformity nonConformity,
            RectificationId rectificationId, String reason) {
        Optional<Finding> existing = findings.findByCriterion(inspectionId, nonConformity.criterionId());
        if (existing.isEmpty()) {
            return;
        }
        Finding finding = existing.get();
        Instant at = clock.now();
        finding.revise(nonConformity.evaluation().result(), nonConformity.evaluation().reasons(),
                nonConformity.evaluation().severity(), nonConformity.presentedEvidence(),
                rectificationId, reason, at, new CorrectiveActionId(ids.newIdentifier()));
        findings.save(finding);
        audit.record(AuditedElementRef.finding(finding.id().value()), AuditAction.FINDING_REVISED,
                AuditDetail.stateChanged("previous evaluation",
                        finding.result() + " (" + finding.severity() + ")"), reason);
    }

    @Override
    public void correctPresentedEvidence(InspectionId inspectionId, CriterionId criterionId,
            List<String> presentedEvidence, RectificationId rectificationId, String reason) {
        Optional<Finding> existing = findings.findByCriterion(inspectionId, criterionId);
        if (existing.isEmpty()) {
            return;
        }
        Finding finding = existing.get();
        if (finding.presentedEvidence().equals(presentedEvidence)) {
            return;
        }
        List<String> previous = finding.presentedEvidence();
        finding.correctPresentedEvidence(presentedEvidence);
        findings.save(finding);
        audit.record(AuditedElementRef.finding(finding.id().value()),
                AuditAction.FINDING_EVIDENCE_CORRECTED,
                AuditDetail.dataChanged(new FieldChange("presented evidence",
                        String.join(", ", previous), String.join(", ", presentedEvidence))),
                reason);
    }

    @Override
    public void voidObligation(InspectionId inspectionId, CriterionId criterionId,
            RectificationId rectificationId, String reason) {
        Optional<Finding> existing = findings.findByCriterion(inspectionId, criterionId);
        if (existing.isEmpty()) {
            return;
        }
        Finding finding = existing.get();
        if (finding.obligationVoided()) {
            return;
        }
        Instant at = clock.now();
        finding.voidObligation(rectificationId, reason, at);
        findings.save(finding);
        audit.record(AuditedElementRef.finding(finding.id().value()),
                AuditAction.FINDING_OBLIGATION_VOIDED,
                AuditDetail.decision("void the obligation to correct",
                        "left without effect by rectification " + rectificationId), reason);
        audit.record(AuditedElementRef.correctiveAction(finding.correctiveAction().id().value()),
                AuditAction.CORRECTIVE_ACTION_VOIDED,
                AuditDetail.stateChanged("open", finding.correctiveAction().status()), reason);
        events.publish(new CorrectiveActionVoided(inspectionId, finding.id(),
                finding.correctiveAction().id(), criterionId, rectificationId, at));
    }

    private void create(InspectionId inspectionId, AssetId assetId, PartyId responsible,
            NonConformity nonConformity) {
        Instant at = clock.now();
        Finding finding = new Finding(
                new FindingId(ids.newIdentifier()),
                inspectionId,
                nonConformity.criterionId(),
                assetId,
                responsible,
                nonConformity.evaluation().result(),
                nonConformity.evaluation().reasons(),
                nonConformity.evaluation().severity(),
                nonConformity.presentedEvidence(),
                new CorrectiveActionId(ids.newIdentifier()),
                at);
        findings.save(finding);
        audit.record(AuditedElementRef.finding(finding.id().value()), AuditAction.FINDING_CREATED,
                AuditDetail.created(finding.result() + " on criterion " + finding.criterionId()
                        + " with severity " + finding.severity() + ", assigned to " + responsible));
    }
}
