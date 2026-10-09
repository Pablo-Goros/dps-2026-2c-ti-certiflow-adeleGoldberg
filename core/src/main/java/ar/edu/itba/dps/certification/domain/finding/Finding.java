package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionClosed;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionExpired;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionPlanned;
import ar.edu.itba.dps.certification.domain.finding.event.CorrectiveActionVoided;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.record.EvaluationReason;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.shared.DomainEvent;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Finding {

    private final FindingId id;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private final InspectionId inspectionId;
    private final CriterionId criterionId;
    private final AssetId assetId;
    private final PartyId responsible;
    private final PartyId inspector;
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
            AssetId assetId, PartyId responsible, PartyId inspector, CriterionResult result,
            List<EvaluationReason> reasons, Severity severity, List<String> presentedEvidence,
            CorrectiveActionId correctiveActionId, Instant createdAt) {
        this.id = Validate.required(id, "finding id");
        this.inspectionId = Validate.required(inspectionId, "inspection id");
        this.criterionId = Validate.required(criterionId, "criterion id");
        this.assetId = Validate.required(assetId, "asset id");
        this.responsible = Validate.required(responsible, "responsible");
        this.inspector = Validate.required(inspector, "inspector");
        this.result = Validate.required(result, "result");
        Validate.ensure(!result.approved(), "an approved criterion does not produce a finding");
        this.reasons = Validate.requiredNonEmpty(reasons, "reasons");
        this.severity = Validate.required(severity, "severity");
        this.presentedEvidence = List.copyOf(Validate.required(presentedEvidence, "presented evidence"));
        this.correctiveActions.add(new CorrectiveAction(
                Validate.required(correctiveActionId, "corrective action id"), inspector,
                Validate.required(createdAt, "creation instant").atZone(ZoneOffset.UTC).toLocalDate()));
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

    public PartyId inspector() {
        return inspector;
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
        CriterionResult validatedResult = Validate.required(newResult, "result");

        Validate.ensure(!validatedResult.approved(),
                "a rectification that approves the criterion voids the obligation instead of revising it");

        List<EvaluationReason> validatedReasons =
                Validate.requiredNonEmpty(newReasons, "reasons");
        Severity validatedSeverity = Validate.required(newSeverity, "severity");
        List<String> validatedEvidence = List.copyOf(
                Validate.required(newPresentedEvidence, "presented evidence"));

        FindingRevision revision = new FindingRevision(
                validatedResult,
                validatedReasons,
                validatedSeverity,
                rectificationId,
                reason,
                revisedAt);

        CorrectiveAction newAction = null;
        if (correctiveAction().status().terminal()) {
            newAction = new CorrectiveAction(
                    Validate.required(replacementAction, "replacement corrective action id"), inspector,
                    revisedAt.atZone(ZoneOffset.UTC).toLocalDate());
        }

        this.result = validatedResult;
        this.reasons = validatedReasons;
        this.severity = validatedSeverity;
        this.presentedEvidence = validatedEvidence;
        revisions.add(revision);

        if (newAction != null) {
            correctiveActions.add(newAction);
        }

        voided = null;
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
        pendingEvents.add(new CorrectiveActionVoided(
                inspectionId, id, correctiveAction().id(), criterionId, rectificationId, voidedAt));
    }

    public List<DomainEvent> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    public void acknowledgeEvent(DomainEvent event) {
        pendingEvents.remove(event);
    }

    public boolean obligationVoided() {
        return voided != null;
    }

    public boolean pendingNonConformity() {
        return !obligationVoided() && !correctionCoversCurrentResult();
    }

    private boolean correctionCoversCurrentResult() {
        return revisionsWhenCorrectionConcluded != null
                && revisionsWhenCorrectionConcluded == revisions.size();
    }

    /** The finding's responsible decides how to correct it (RF8); nobody else can confirm the plan. */
    public void planCorrection(PartyId planner, CorrectionPlan plan, LocalDate today, Instant at) {
        Validate.required(planner, "planner");
        Validate.ensure(planner.equals(responsible), "only the responsible " + responsible
                + " may plan the correction of finding " + id + ", not " + planner);
        Validate.required(at, "planning instant");
        correctiveAction().confirmPlan(plan, today);
        pendingEvents.add(new CorrectiveActionPlanned(inspectionId, criterionId, at));
    }

    public void reportCorrectionExecution(ExecutionReport report) {
        correctiveAction().reportExecution(report);
    }

    public boolean expireCorrectionIfOverdue(LocalDate today, Instant at) {
        Validate.required(at, "expiration instant");
        if (!correctiveAction().expireIfOverdue(today)) {
            return false;
        }
        pendingEvents.add(new CorrectiveActionExpired(
                inspectionId, id, correctiveAction().id(), criterionId, at));
        return true;
    }

    public boolean concludeCorrection(Verification verification, LocalDate today) {
        boolean closed = correctiveAction().verify(verification, today);
        if (closed) {
            revisionsWhenCorrectionConcluded = revisions.size();
            pendingEvents.add(new CorrectiveActionClosed(
                    inspectionId, id, correctiveAction().id(), criterionId, verification.verifiedAt()));
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
