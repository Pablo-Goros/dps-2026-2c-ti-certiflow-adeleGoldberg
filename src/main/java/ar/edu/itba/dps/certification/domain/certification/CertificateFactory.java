package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.issuance.*;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;

import java.util.Optional;

/** The only production construction path, loading the facts and enforcing the policy. */
public final class CertificateFactory {
    private final InspectionQuery inspections;
    private final FindingQuery findings;
    private final CertificateRepository certificates;
    private final CertificateIssuancePolicy policy;
    private final CertificateValidityPolicy validityPolicy;
    private final IdGenerator ids;
    private final Clock clock;

    public CertificateFactory(InspectionQuery inspections, FindingQuery findings,
            CertificateRepository certificates, CertificateIssuancePolicy policy,
            CertificateValidityPolicy validityPolicy, IdGenerator ids, Clock clock) {
        this.inspections = inspections;
        this.findings = findings;
        this.certificates = certificates;
        this.policy = policy;
        this.validityPolicy = validityPolicy;
        this.ids = ids;
        this.clock = clock;
    }

    public IssuanceDecision issue(InspectionId inspectionId) {
        return create(inspections.require(inspectionId), null);
    }

    public IssuanceDecision renew(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        var existing = certificates.findByBackingInspection(inspectionId);
        if (existing.isPresent() && existing.get().previousCertificateId().isPresent()) {
            return new IssuanceDecision.AlreadyIssued(existing.get().id(), existing.get().status());
        }
        Certificate previous = certificates.findLatestForAsset(inspection.assetId())
                .orElseThrow(() -> new DomainException("asset " + inspection.assetId() + " has no certificate to renew"));
        Validate.ensure(previous.validity().expiredAt(clock.now()),
                "certificate " + previous.id() + " has not expired yet, so it cannot be renewed");
        Validate.ensure(!inspection.id().equals(previous.backingInspectionId()),
                "renewal requires a new inspection");
        Validate.ensure(inspection.startedAt().filter(at -> at.isAfter(previous.validity().issuedAt())).isPresent(),
                "renewal requires an inspection started after the previous certificate was issued");
        return create(inspection, previous);
    }

    private IssuanceDecision create(Inspection inspection, Certificate previous) {
        var existing = certificates.findByBackingInspection(inspection.id());
        if (existing.isPresent()) {
            return new IssuanceDecision.AlreadyIssued(existing.get().id(), existing.get().status());
        }
        var at = clock.now();
        var live = certificates.findNonExpiredForAsset(inspection.assetId())
                .filter(c -> c.coversMoment(at)).map(Certificate::id);
        CertificationContext context = new CertificationContext(inspection, findings.findingsOf(inspection.id()),
                clock.today(), Optional.empty(), live);
        var blockers = policy.blockersFor(context);
        if (!blockers.isEmpty()) { return new IssuanceDecision.Blocked(blockers); }
        // The factory's structural safeguards hold even with additional/custom requirements.
        Validate.ensure(inspection.status().closed(), "the backing inspection must be closed");
        Validate.ensure(!inspection.closedAt().orElseThrow().isAfter(at), "issuance cannot precede closure");
        var validity = validityPolicy.validityFrom(at);
        Validate.ensure(validity.issuedAt().equals(at), "validity must begin at issuance");
        return new IssuanceDecision.Issued(new Certificate(new CertificateId(ids.newIdentifier()),
                inspection.assetId(), inspection.id(), inspection.requireFrozenSchemaVersionId(),
                validity, previous == null ? null : previous.id()));
    }
}
