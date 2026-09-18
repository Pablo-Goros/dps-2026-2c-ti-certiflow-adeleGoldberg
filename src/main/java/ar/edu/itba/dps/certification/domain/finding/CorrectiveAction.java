package ar.edu.itba.dps.certification.domain.finding;

import ar.edu.itba.dps.certification.domain.finding.action.CorrectionPlan;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.finding.action.ExecutionReport;
import ar.edu.itba.dps.certification.domain.finding.action.Verification;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CorrectiveAction {

    private final CorrectiveActionId id;
    private final List<ExecutionReport> executions = new ArrayList<>();
    private final List<Verification> verifications = new ArrayList<>();
    private CorrectiveActionStatus status = CorrectiveActionStatus.PENDING_PLANNING;
    private CorrectionPlan plan;
    private Instant closedAt;
    private boolean deadlineBreached;
    private VoidedObligation voided;

    public CorrectiveAction(CorrectiveActionId id) {
        this.id = Validate.required(id, "corrective action id");
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

    void confirmPlan(CorrectionPlan newPlan) {
        Validate.required(newPlan, "correction plan");
        if (status != CorrectiveActionStatus.PENDING_PLANNING) {
            throw new DomainException("corrective action " + id
                    + " has already been planned and its plan cannot be modified");
        }
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
        if (status != CorrectiveActionStatus.EXECUTION_REPORTED) {
            throw new DomainException("corrective action " + id
                    + " has no reported execution to verify, it is " + status);
        }
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
        if (status.terminal() || plan == null || !plan.overdueOn(today)) {
            return false;
        }
        boolean newlyBreached = !deadlineBreached;
        deadlineBreached = true;
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
        return status.open() && plan != null && plan.overdueOn(today);
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
        if (plan != null && plan.overdueOn(today)) {
            deadlineBreached = true;
        }
    }
}
