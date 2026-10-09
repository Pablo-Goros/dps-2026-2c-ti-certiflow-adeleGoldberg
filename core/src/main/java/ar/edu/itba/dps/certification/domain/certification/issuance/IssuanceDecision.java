package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public sealed interface IssuanceDecision {

    record Issued(Certificate certificate) implements IssuanceDecision {

        public Issued {
            Validate.required(certificate, "certificate");
        }
    }

    record AlreadyIssued(CertificateId certificateId, CertificateStatus status,
            CertificationPolicySnapshot policy,
            CertificateMode mode)
            implements IssuanceDecision {

        public AlreadyIssued {
            Validate.required(certificateId, "certificate id");
            Validate.required(status, "status");
            Validate.required(policy, "policy snapshot"); Validate.required(mode, "mode");
            Validate.ensure(mode != CertificateMode.CONDITIONAL || policy.allowsConditional(), "conditional mode is forbidden");
        }
    }

    record Blocked(CertificationAssessment assessment) implements IssuanceDecision {

        public Blocked {
            Validate.required(assessment, "assessment");
            Validate.requiredNonEmpty(assessment.blockers(), "blockers");
        }

        public List<IssuanceBlocker> blockers() { return assessment.blockers(); }

        public String describe() {
            return blockers().stream().map(IssuanceBlocker::describe)
                    .reduce((left, right) -> left + "; " + right).orElseThrow();
        }
    }
}
