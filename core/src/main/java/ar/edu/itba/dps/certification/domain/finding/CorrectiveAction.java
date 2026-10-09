package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CorrectiveAction {

    private static final int PLANNING_DAYS = 30;
    private final CorrectiveActionId id;
    private final LocalDate planningDueDate;
    private final PartyId inspector;
    private final List<ExecutionReport> executions = new ArrayList<>();
    private final List<Verification> verifications = new ArrayList<>();
    private CorrectiveActionStatus status = CorrectiveActionStatus.PENDING_PLANNING;
    private CorrectionPlan plan;
    private Instant closedAt;
    private boolean deadlineBreached;
    private LocalDate breachedDeadline;
    private boolean expiryNotified;
    private VoidedObligation voided;

    CorrectiveAction(CorrectiveActionId id, PartyId inspector,
            LocalDate createdOn) {
        this.id = Validate.required(id, "corrective action id");
        this.inspector = Validate.required(inspector, "inspector");
        this.planningDueDate = Validate.required(createdOn, "creation date").plusDays(PLANNING_DAYS);
    }

    public LocalDate planningDueDate() {
        return planningDueDate;
    }

    public LocalDate deadline() {
        return plan == null ? planningDueDate : plan.dueDate();
    }

    public CorrectiveActionId id() {
        return id;
    }

    public CorrectiveActionStatus status() {
        return status;
    }

    public Optional<CorrectionPlan> plan() {
        return Optional.ofNullable(plan);
    }

    public List<ExecutionReport> executions() {
        return List.copyOf(executions);
    }

    public List<Verification> verifications() {
        return List.copyOf(verifications);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Optional<VoidedObligation> voided() {
        return Optional.ofNullable(voided);
    }

    public boolean deadlineBreached() {
        return deadlineBreached;
    }

    public Optional<LocalDate> breachedDeadline() {
        return Optional.ofNullable(breachedDeadline);
    }

    void confirmPlan(CorrectionPlan newPlan, LocalDate today) {
        Validate.required(newPlan, "correction plan");
        if (status != CorrectiveActionStatus.PENDING_PLANNING) {
            throw new DomainException("corrective action " + id
                    + " has already been planned and its plan cannot be modified");
        }
        Validate.required(today, "planning date");
        Validate.ensure(!newPlan.dueDate().isBefore(today), "correction deadline cannot precede planning date");
        Validate.ensure(!newPlan.executor().equals(inspector), "the inspector cannot execute their own correction");
        latchBreachIfOverdue(today);
        this.plan = newPlan;
        this.status = CorrectiveActionStatus.PLANNED;
    }

    void reportExecution(ExecutionReport report) {
        Validate.required(report, "execution report");
        if (!status.planned()) {
            throw new DomainException("cannot report execution of corrective action " + id
                    + " while it is " + status);
        }
        Validate.ensure(report.reportedBy().equals(plan.executor()),
                "corrective action " + id + " was assigned to " + plan.executor()
                        + ", so " + report.reportedBy() + " cannot report its execution");
        executions.add(report);
        status = CorrectiveActionStatus.EXECUTION_REPORTED;
    }

    boolean verify(Verification verification, LocalDate today) {
        Validate.required(verification, "verification");
        Validate.required(today, "verification date");
        if (status != CorrectiveActionStatus.EXECUTION_REPORTED) {
            throw new DomainException("corrective action " + id
                    + " has no reported execution to verify, it is " + status);
        }
        Validate.ensure(verification.verifiedBy().equals(inspector),
                "only the inspector may verify the corrective action");
        Validate.ensure(!verification.verifiedBy().equals(plan.executor()),
                "the executor cannot verify their own correction");
        verifications.add(verification);
        if (!verification.satisfactory()) {
            status = CorrectiveActionStatus.PLANNED;
            return false;
        }
        status = CorrectiveActionStatus.CLOSED;
        closedAt = verification.verifiedAt();
        latchBreachIfOverdue(today);
        return true;
    }

    boolean expireIfOverdue(LocalDate today) {
        Validate.required(today, "today");
        if (status.terminal() || (!deadlineBreached && !today.isAfter(deadline()))) {
            return false;
        }
        boolean newlyBreached = !expiryNotified;
        latchBreachIfOverdue(today);
        expiryNotified = true;
        return newlyBreached;
    }

    void voidObligation(VoidedObligation voidRecord) {
        Validate.required(voidRecord, "voided obligation");
        if (voided != null) {
            return;
        }
        this.voided = voidRecord;
        if (status != CorrectiveActionStatus.CLOSED) {
            this.status = CorrectiveActionStatus.VOIDED;
        }
    }

    public boolean overdueAndOpen(LocalDate today) {
        return status.open() && (deadlineBreached || today.isAfter(deadline()));
    }

    public boolean awaitingPlan() {
        return status == CorrectiveActionStatus.PENDING_PLANNING;
    }

    public boolean metItsDeadline() {
        return status == CorrectiveActionStatus.CLOSED && !deadlineBreached;
    }

    public boolean blocksCertification() {
        return voided == null && status != CorrectiveActionStatus.CLOSED;
    }

    private void latchBreachIfOverdue(LocalDate today) {
        if (today.isAfter(deadline())) {
            deadlineBreached = true;
            if (breachedDeadline == null) {
                breachedDeadline = deadline();
            }
        }
    }
}
