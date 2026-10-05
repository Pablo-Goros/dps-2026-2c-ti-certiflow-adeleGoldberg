package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Evaluates a new request, even if the inspection already backed a certificate. */
public record CertificationAssessment(InspectionId inspectionId, CertificateScope scope,
        Instant evaluatedAt, CertificationPolicySnapshot policy, List<IssuanceBlocker> blockers,
        Optional<CertificateMode> mode) {
    public CertificationAssessment {
        Validate.required(inspectionId, "inspection id");
        Validate.required(scope, "scope");
        Validate.required(evaluatedAt, "evaluation instant");
        Validate.required(policy, "policy snapshot");
        blockers = List.copyOf(Validate.required(blockers, "blockers"));
        Validate.required(mode, "mode");
        Validate.ensure(blockers.isEmpty() == mode.isPresent(), "only eligible assessments have a mode");
        Validate.ensure(mode.orElse(CertificateMode.REGULAR) != CertificateMode.CONDITIONAL
                || policy.allowsConditional(), "the policy must allow the conditional mode");
    }
}
