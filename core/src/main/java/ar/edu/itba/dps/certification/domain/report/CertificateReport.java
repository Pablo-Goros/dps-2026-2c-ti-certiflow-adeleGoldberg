package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateStatus;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionStatus;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record CertificateReport(
        CertificateId certificateId,
        AssetId assetId,
        InspectionId backingInspectionId,
        SchemaVersionId schemaVersionId,
        CertificateScope scope,
        Instant issuedAt,
        Instant expiresAt,
        CertificateStatus status,
        Optional<CertificateId> previousCertificateId,
        boolean backingInspectionWasRectified,
        List<String> unresolvedSuspensionCauses,
        List<PendingCommitment> pendingCommitments,
        CertificationPolicySnapshot policy,
        CertificateMode mode) {

    public CertificateReport {
        Validate.required(policy, "policy"); Validate.required(mode, "mode");
        Validate.required(certificateId, "certificate id");
        Validate.required(assetId, "asset id");
        Validate.required(backingInspectionId, "backing inspection id");
        Validate.required(schemaVersionId, "schema version id");
        Validate.required(scope, "certificate scope");
        Validate.required(issuedAt, "issue instant");
        Validate.required(expiresAt, "expiry instant");
        Validate.ensure(expiresAt.isAfter(issuedAt), "report validity must end after issuance");
        Validate.ensure(mode != CertificateMode.CONDITIONAL || policy.allowsConditional(), "conditional mode is forbidden");
        Validate.required(status, "status");
        Validate.required(previousCertificateId, "previous certificate id");
        unresolvedSuspensionCauses =
                List.copyOf(Validate.required(unresolvedSuspensionCauses, "suspension causes"));
        pendingCommitments = List.copyOf(Validate.required(pendingCommitments, "pending commitments"));
    }

    public record PendingCommitment(CriterionId criterionId, String work, LocalDate dueDate,
            CorrectiveActionStatus status) {

        public PendingCommitment {
            Validate.required(criterionId, "criterion id");
            work = Validate.requiredText(work, "planned work");
            Validate.required(dueDate, "due date");
            Validate.required(status, "status");
        }
    }
}
