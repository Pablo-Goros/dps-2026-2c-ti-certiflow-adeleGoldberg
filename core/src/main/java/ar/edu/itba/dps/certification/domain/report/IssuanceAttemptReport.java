package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

public record IssuanceAttemptReport(
        InspectionId inspectionId,
        boolean certified,
        Optional<CertificateId> certificateId,
        List<String> blockingReasons,
        CertificateScope scope,
        CertificationPolicySnapshot policy,
        Optional<CertificateMode> mode) {

    public IssuanceAttemptReport {
        Validate.required(scope, "scope"); Validate.required(policy, "policy"); Validate.required(mode, "mode");
        Validate.ensure(mode.orElse(CertificateMode.REGULAR) != CertificateMode.CONDITIONAL || policy.allowsConditional(), "conditional mode is forbidden");
        Validate.ensure(certified == mode.isPresent(), "only certified attempts have a mode");
        Validate.required(inspectionId, "inspection id");
        Validate.required(certificateId, "certificate id");
        blockingReasons = List.copyOf(Validate.required(blockingReasons, "blocking reasons"));
        Validate.ensure(certified == certificateId.isPresent(),
                "an issuance attempt either certifies a certificate or remains uncertified");
        Validate.ensure(certified == blockingReasons.isEmpty(),
                "a certified issuance has no blockers and a blocked attempt reports them");
    }

    public static IssuanceAttemptReport certified(Certificate certificate) {
        Validate.required(certificate, "certificate");
        return new IssuanceAttemptReport(certificate.backingInspectionId(), true, Optional.of(certificate.id()),
                List.of(), certificate.scope(), certificate.policy(), Optional.of(certificate.mode()));
    }
}
