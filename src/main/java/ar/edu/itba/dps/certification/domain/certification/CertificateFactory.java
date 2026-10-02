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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The only production construction path for certificates. It gathers the facts through domain
 * ports and applies the issuance policy, so eligibility queries and actual issuance can never
 * disagree: both go through {@link #contextFor(Inspection)}.
 */
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

    /** What would prevent issuing a certificate backed by this inspection right now. */
    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId) {
        return policy.blockersFor(contextFor(inspections.require(inspectionId)));
    }

    /**
     * Issues the first certificate of an asset. Once an asset has been certified and that
     * certificate expired, the next one is a renewal and must keep the link with its predecessor
     * (RF9), so issuing an unrelated certificate is refused.
     */
    public IssuanceDecision issue(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        Optional<IssuanceDecision> alreadyIssued = alreadyIssued(inspectionId);
        if (alreadyIssued.isPresent()) {
            return alreadyIssued.get();
        }
        Instant now = clock.now();
        certificates.findLatestForAsset(inspection.assetId())
                .filter(previous -> previous.status().expired() || previous.validity().expiredAt(now))
                .ifPresent(previous -> {
                    throw new DomainException("asset " + inspection.assetId() + " was certified by "
                            + previous.id() + ", which has expired; renew it to keep the link between both");
                });
        return create(inspection, null);
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

    private Optional<IssuanceDecision> alreadyIssued(InspectionId inspectionId) {
        return certificates.findByBackingInspection(inspectionId)
                .map(existing -> new IssuanceDecision.AlreadyIssued(existing.id(), existing.status()));
    }

    private IssuanceDecision create(Inspection inspection, Certificate previous) {
        Optional<IssuanceDecision> alreadyIssued = alreadyIssued(inspection.id());
        if (alreadyIssued.isPresent()) {
            return alreadyIssued.get();
        }
        var blockers = policy.blockersFor(contextFor(inspection));
        if (!blockers.isEmpty()) { return new IssuanceDecision.Blocked(blockers); }
        // The factory's structural safeguards hold even with additional/custom requirements.
        var at = clock.now();
        Validate.ensure(inspection.status().closed(), "the backing inspection must be closed");
        Validate.ensure(!inspection.closedAt().orElseThrow().isAfter(at), "issuance cannot precede closure");
        var validity = validityPolicy.validityFrom(at);
        Validate.ensure(validity.issuedAt().equals(at), "validity must begin at issuance");
        return new IssuanceDecision.Issued(new Certificate(new CertificateId(ids.newIdentifier()),
                inspection.assetId(), inspection.id(), inspection.requireFrozenSchemaVersionId(),
                validity, previous == null ? null : previous.id()));
    }

    private CertificationContext contextFor(Inspection inspection) {
        Instant now = clock.now();
        var liveCertificate = certificates.findNonExpiredForAsset(inspection.assetId())
                .filter(certificate -> certificate.coversMoment(now))
                .filter(certificate -> !certificate.backingInspectionId().equals(inspection.id()))
                .map(Certificate::id);
        return new CertificationContext(inspection, findings.findingsOf(inspection.id()), clock.today(),
                laterClosedInspectionOf(inspection), liveCertificate);
    }

    /** A closed inspection of the same asset started after this one describes a newer state. */
    private Optional<InspectionId> laterClosedInspectionOf(Inspection inspection) {
        if (inspection.startedAt().isEmpty()) {
            return Optional.empty();
        }
        Instant startedAt = inspection.startedAt().get();
        return inspections.findClosedByAsset(inspection.assetId()).stream()
                .filter(other -> !other.id().equals(inspection.id()))
                .filter(other -> other.startedAt().filter(at -> at.isAfter(startedAt)).isPresent())
                .max(Comparator.comparing(other -> other.startedAt().orElseThrow()))
                .map(Inspection::id);
    }
}
