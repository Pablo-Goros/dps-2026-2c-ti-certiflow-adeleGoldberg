package ar.edu.itba.dps.certification.app.web.dto;

public final class CertificateDtos {

    private CertificateDtos() {
    }

    /** {@code subsystem} is optional: absent means a global certificate, present a partial one (F1). */
    public record IssueRequest(String subsystem) {
    }
}
