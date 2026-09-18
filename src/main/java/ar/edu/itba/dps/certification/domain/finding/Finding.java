package ar.edu.itba.dps.certification.domain.finding;

import java.time.LocalDate;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveAction;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Finding {

    private final FindingId id;
    private final InspectionId inspectionId;
    private final CriterionId criterionId;
    private final AssetId assetId;
    private final PartyId responsible;
    private final Instant createdAt;
    private final List<CorrectiveAction> correctiveActions = new ArrayList<>();
    private final List<FindingRevision> revisions = new ArrayList<>();
    private CriterionResult result;
    private List<EvaluationReason> reasons;
    private Severity severity;
    private List<String> presentedEvidence;
    private VoidedObligation voided;
    private Integer revisionsWhenCorrectionConcluded;

    public Finding(FindingId id, InspectionId inspectionId, CriterionId criterionId,
            AssetId assetId, PartyId responsible, CriterionResult result,
            List<EvaluationReason> reasons, Severity severity, List<String> presentedEvidence,
            CorrectiveActionId correctiveActionId, Instant createdAt) {
        this.id = Validate.required(id, "finding id");
        this.inspectionId = Validate.required(inspectionId, "inspection id");
        this.criterionId = Validate.required(criterionId, "criterion id");
        this.assetId = Validate.required(assetId, "asset id");
        this.responsible = Validate.required(responsible, "responsible");
        this.result = Validate.required(result, "result");
        Validate.ensure(!result.approved(), "an approved criterion does not produce a finding");
        this.reasons = Validate.requiredNonEmpty(reasons, "reasons");
        this.severity = Validate.required(severity, "severity");
        this.presentedEvidence = List.copyOf(Validate.required(presentedEvidence, "presented evidence"));
        this.correctiveActions.add(new CorrectiveAction(
                Validate.required(correctiveActionId, "corrective action id")));
        this.createdAt = Validate.required(createdAt, "creation instant");
    }

    public FindingId id() {
        return id;
    }

    public InspectionId inspectionId() {
        return inspectionId;
    }

    public CriterionId criterionId() {
        return criterionId;
    }

    public AssetId assetId() {
        return assetId;
    }

    public PartyId responsible() {
        return responsible;
    }

    public CriterionResult result() {
        return result;
    }

    public List<EvaluationReason> reasons() {
        return List.copyOf(reasons);
    }

    public Severity severity() {
        return severity;
    }

    public List<String> presentedEvidence() {
        return presentedEvidence;
    }

    public List<EvidenceShortfall> shortfalls() {
        return reasons.stream()
                .filter(EvaluationReason.MissingEvidence.class::isInstance)
                .map(reason -> ((EvaluationReason.MissingEvidence) reason).shortfall())
                .toList();
    }

    public Instant createdAt() {
        return createdAt;
    }

    public CorrectiveAction correctiveAction() {
        return correctiveActions.getLast();
    }

    public List<CorrectiveAction> correctiveActions() {
        return List.copyOf(correctiveActions);
    }

    public List<FindingRevision> revisions() {
        return List.copyOf(revisions);
    }

    public Optional<VoidedObligation> voided() {
        return Optional.ofNullable(voided);
    }

    public void revise(CriterionResult newResult, List<EvaluationReason> newReasons,
            Severity newSeverity, List<String> newPresentedEvidence, RectificationId rectificationId,
            String reason, Instant revisedAt, CorrectiveActionId replacementAction) {
        Validate.ensure(!newResult.approved(),
                "a rectification that approves the criterion voids the obligation instead of revising it");
        this.result = Validate.required(newResult, "result");
        this.reasons = Validate.requiredNonEmpty(newReasons, "reasons");
        this.severity = Validate.required(newSeverity, "severity");
        this.presentedEvidence = List.copyOf(
                Validate.required(newPresentedEvidence, "presented evidence"));
        revisions.add(new FindingRevision(newResult, newReasons, newSeverity, rectificationId, reason,
                revisedAt));
        if (correctiveAction().status().terminal()) {
            correctiveActions.add(new CorrectiveAction(
                    Validate.required(replacementAction, "replacement corrective action id")));
        }
    }

    public void correctPresentedEvidence(List<String> corrected) {
        this.presentedEvidence = List.copyOf(Validate.required(corrected, "presented evidence"));
    }

    public void voidObligation(RectificationId rectificationId, String reason, Instant voidedAt) {
        if (voided != null) {
            return;
        }
        VoidedObligation record = new VoidedObligation(rectificationId, reason, voidedAt);
        this.voided = record;
        correctiveAction().voidObligation(record);
        revisionsWhenCorrectionConcluded = revisions.size();
    }

    public boolean obligationVoided() {
        return voided != null;
    }

    public boolean blocksCertification() {
        return result == CriterionResult.REJECTED && !correctionCoversCurrentResult();
    }

    private boolean correctionCoversCurrentResult() {
        return revisionsWhenCorrectionConcluded != null
                && revisionsWhenCorrectionConcluded == revisions.size();
    }

    public boolean concludeCorrection(Verification verification, LocalDate today) {
        boolean closed = correctiveAction().verify(verification, today);
        if (closed) {
            revisionsWhenCorrectionConcluded = revisions.size();
        }
        return closed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Finding that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Finding " + id + " on " + criterionId + " (" + result + ")";
    }
}
