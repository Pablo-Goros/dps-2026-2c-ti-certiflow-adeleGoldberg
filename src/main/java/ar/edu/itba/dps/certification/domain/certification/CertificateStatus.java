package ar.edu.itba.dps.certification.domain.certification;

public enum CertificateStatus {
    VALID, SUSPENDED, EXPIRED;

    public boolean expired() {
        return this == EXPIRED;
    }
}
