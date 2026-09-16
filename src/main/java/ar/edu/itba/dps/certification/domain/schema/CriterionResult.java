package ar.edu.itba.dps.certification.domain.schema;

public enum CriterionResult {
    APPROVED, OBSERVED, REJECTED;

    public boolean approved() {
        return this == APPROVED;
    }

    public CriterionResult worstOf(CriterionResult other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
