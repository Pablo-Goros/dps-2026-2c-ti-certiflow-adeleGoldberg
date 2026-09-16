package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;

import java.util.List;

public final class Decisions {

    private Decisions() {
    }

    public static Certificate issuedCertificate(IssuanceDecision decision) {
        return switch (decision) {
            case IssuanceDecision.Issued issued -> issued.certificate();
            case IssuanceDecision.AlreadyIssued already -> throw new AssertionError(
                    "expected a newly issued certificate, but " + already.certificateId()
                            + " already backed this inspection");
            case IssuanceDecision.Blocked blocked -> throw new AssertionError(
                    "expected a certificate, but issuance was blocked: " + blocked.describe());
        };
    }

    public static List<IssuanceBlocker> blockers(IssuanceDecision decision) {
        return switch (decision) {
            case IssuanceDecision.Blocked blocked -> blocked.blockers();
            case IssuanceDecision.Issued issued -> throw new AssertionError(
                    "expected issuance to be blocked, but certificate " + issued.certificate().id()
                            + " was issued");
            case IssuanceDecision.AlreadyIssued already -> throw new AssertionError(
                    "expected issuance to be blocked, but " + already.certificateId()
                            + " already existed");
        };
    }

    public static CertificateId alreadyIssuedCertificate(IssuanceDecision decision) {
        return switch (decision) {
            case IssuanceDecision.AlreadyIssued already -> already.certificateId();
            case IssuanceDecision.Issued issued -> throw new AssertionError(
                    "expected the existing certificate to be reported, but "
                            + issued.certificate().id() + " was newly issued");
            case IssuanceDecision.Blocked blocked -> throw new AssertionError(
                    "expected the existing certificate to be reported, but issuance was blocked: "
                            + blocked.describe());
        };
    }
}
