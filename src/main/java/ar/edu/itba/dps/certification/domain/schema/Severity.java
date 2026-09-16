package ar.edu.itba.dps.certification.domain.schema;

public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    public Severity maxOf(Severity other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
