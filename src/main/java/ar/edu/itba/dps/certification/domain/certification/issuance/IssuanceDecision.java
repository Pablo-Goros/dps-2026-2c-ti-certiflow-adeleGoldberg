package ar.edu.itba.dps.certification.domain.certification.issuance;

import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

public sealed interface IssuanceDecision {

    record Issued(Certificate certificate) implements IssuanceDecision {

        public Issued {
            Validate.required(certificate, "certificate");
        }
    }

    record AlreadyIssued(CertificateId certificateId, CertificateStatus status)
            implements IssuanceDecision {

        public AlreadyIssued {
            Validate.required(certificateId, "certificate id");
            Validate.required(status, "status");
        }
    }

    record Blocked(List<IssuanceBlocker> blockers) implements IssuanceDecision {

        public Blocked {
            blockers = Validate.requiredNonEmpty(blockers, "blockers");
        }

        public String describe() {
            return blockers.stream().map(IssuanceBlocker::describe)
                    .reduce((left, right) -> left + "; " + right).orElseThrow();
        }
    }
}
