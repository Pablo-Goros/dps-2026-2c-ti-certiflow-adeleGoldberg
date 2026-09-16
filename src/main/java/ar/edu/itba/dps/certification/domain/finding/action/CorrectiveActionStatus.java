package ar.edu.itba.dps.certification.domain.finding.action;

public enum CorrectiveActionStatus {
    PENDING_PLANNING, PLANNED, EXECUTION_REPORTED, CLOSED, VOIDED;

    public boolean terminal() {
        return this == CLOSED || this == VOIDED;
    }

    public boolean open() {
        return !terminal();
    }

    public boolean planned() {
        return this == PLANNED || this == EXECUTION_REPORTED;
    }
}
