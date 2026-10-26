package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class CertificateDtos {

    private CertificateDtos() {
    }

    /** {@code subsystem} is optional: absent means a global certificate, present a partial one (F1). */
    public record IssueRequest(String subsystem) {
    }

    /** API representation of a decision; it never exposes the internal facts held by its proof. */
    public record AssessmentResponse(InspectionId inspectionId, CertificateScope scope, Instant evaluatedAt,
            CertificationPolicySnapshot policy, List<IssuanceBlocker> blockers, Optional<CertificateMode> mode) {
        public static AssessmentResponse from(CertificationAssessment assessment) {
            return new AssessmentResponse(assessment.inspectionId(), assessment.scope(), assessment.evaluatedAt(),
                    assessment.policy(), assessment.blockers(), assessment.mode());
        }
    }
}
