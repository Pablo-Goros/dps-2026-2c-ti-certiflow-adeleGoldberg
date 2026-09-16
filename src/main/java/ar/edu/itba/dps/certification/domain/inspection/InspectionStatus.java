package ar.edu.itba.dps.certification.domain.inspection;

public enum InspectionStatus {
    ASSIGNED, IN_PROGRESS, CLOSED;

    public boolean open() {
        return this == IN_PROGRESS;
    }

    public boolean closed() {
        return this == CLOSED;
    }
}
