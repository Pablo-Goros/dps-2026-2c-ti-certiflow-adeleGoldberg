package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.CertificationContextAssembler;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionSummary;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificateIssuancePolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class IssueCertificate {

    private final InspectionQuery inspections;
    private final CertificateRepository certificates;
    private final CertificationContextAssembler assembler;
    private final CertificateIssuancePolicy policy;
    private final CertificateValidityPolicy validityPolicy;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditRecorder audit;

    public IssueCertificate(InspectionQuery inspections, CertificateRepository certificates,
            CertificationContextAssembler assembler, CertificateIssuancePolicy policy,
            CertificateValidityPolicy validityPolicy, IdGenerator ids, Clock clock, AuditRecorder audit) {
        this.inspections = inspections;
        this.certificates = certificates;
        this.assembler = assembler;
        this.policy = policy;
        this.validityPolicy = validityPolicy;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public IssuanceDecision issue(InspectionId inspectionId) {
        return issue(inspectionId, Optional.empty());
    }

    IssuanceDecision issue(InspectionId inspectionId,
            Optional<CertificateId> previousCertificateId) {
        Validate.required(previousCertificateId, "previous certificate reference");
        InspectionSummary inspection = inspections.summaryOf(inspectionId);
        previousCertificateId.ifPresent(previous -> Validate.ensure(
                certificates.require(previous).assetId().equals(inspection.assetId()),
                "certificate " + previous + " belongs to another asset and cannot precede a "
                        + "certificate for asset " + inspection.assetId()));

        Optional<Certificate> existing = certificates.findByBackingInspection(inspectionId);
        if (existing.isPresent()) {
            return new IssuanceDecision.AlreadyIssued(existing.get().id(), existing.get().status());
        }

        List<IssuanceBlocker> blockers =
                policy.blockersFor(assembler.contextFor(inspection));
        Instant at = clock.now();
        if (!blockers.isEmpty()) {
            IssuanceDecision.Blocked blocked = new IssuanceDecision.Blocked(blockers);
            audit.record(AuditedElementRef.inspection(inspectionId.value()),
                    AuditAction.CERTIFICATE_ISSUANCE_BLOCKED,
                    AuditDetail.decision("issue a certificate", "blocked: " + blocked.describe()));
            return blocked;
        }

        Certificate certificate = new Certificate(
                new CertificateId(ids.newIdentifier()),
                inspection.assetId(),
                inspectionId,
                inspection.requireSchemaVersionId(),
                validityPolicy.validityFrom(at),
                previousCertificateId.orElse(null));
        certificates.save(certificate);
        audit.record(AuditedElementRef.certificate(certificate.id().value()),
                AuditAction.CERTIFICATE_ISSUED,
                AuditDetail.created("certificate for asset " + certificate.assetId()
                        + " backed by inspection " + inspectionId + ", valid until "
                        + certificate.validity().expiresAt()));
        return new IssuanceDecision.Issued(certificate);
    }
}
