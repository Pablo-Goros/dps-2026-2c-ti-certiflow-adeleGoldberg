package ar.edu.itba.dps.certification.application.certification;

import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.finding.port.FindingQuery;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.CertificateIssuer;
import ar.edu.itba.dps.certification.domain.certification.CertificateScope;
import ar.edu.itba.dps.certification.domain.certification.CertificateValidityPolicy;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificateDerivation;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalCertificatePolicy;
import ar.edu.itba.dps.certification.domain.certification.derivation.GlobalDerivationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificateIssuancePolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationContext;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The only production construction path for certificates. It gathers the facts through application
 * ports and applies the issuance policy, so eligibility queries and actual issuance can never
 * disagree: both go through {@link #contextFor(Inspection, CertificateScope)}.
 */
public final class CertificateFactory {
    private final InspectionQuery inspections;
    private final FindingQuery findings;
    private final CertificateRepository certificates;
    private final CertificateIssuancePolicy policy;
    private final CertificateValidityPolicy validityPolicy;
    private final GlobalCertificatePolicy globalPolicy;
    private final CertificateIssuer issuer;
    private final IdGenerator ids;
    private final Clock clock;

    public CertificateFactory(InspectionQuery inspections, FindingQuery findings,
            CertificateRepository certificates, CertificateIssuancePolicy policy,
            CertificateValidityPolicy validityPolicy, GlobalCertificatePolicy globalPolicy,
            IdGenerator ids, Clock clock) {
        this.inspections = inspections;
        this.findings = findings;
        this.certificates = certificates;
        this.policy = policy;
        this.validityPolicy = validityPolicy;
        this.globalPolicy = globalPolicy;
        this.issuer = new CertificateIssuer();
        this.ids = ids;
        this.clock = clock;
    }

    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        return policy.blockersFor(contextFor(inspection, CertificateScope.global()));
    }

    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId, Subsystem subsystem) {
        Inspection inspection = inspections.require(inspectionId);
        requireDeclaredSubsystem(inspection, subsystem);
        return policy.blockersFor(contextFor(inspection, CertificateScope.of(subsystem)));
    }

    /**
     * Issues the first certificate of an asset. Once an asset has been certified and that
     * certificate expired, the next one is a renewal and must keep the link with its predecessor
     * (RF9), so issuing an unrelated certificate is refused.
     */
    public IssuanceDecision issue(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        requireWholeAssetSchema(inspection);
        return issueWithin(inspection, CertificateScope.global());
    }

    public IssuanceDecision issuePartial(InspectionId inspectionId, Subsystem subsystem) {
        Inspection inspection = inspections.require(inspectionId);
        requireDeclaredSubsystem(inspection, subsystem);
        return issueWithin(inspection, CertificateScope.of(subsystem));
    }

    public IssuanceDecision renew(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        requireWholeAssetSchema(inspection);
        return renewWithin(inspection, CertificateScope.global());
    }

    public IssuanceDecision renewPartial(InspectionId inspectionId, Subsystem subsystem) {
        Inspection inspection = inspections.require(inspectionId);
        requireDeclaredSubsystem(inspection, subsystem);
        return renewWithin(inspection, CertificateScope.of(subsystem));
    }

    public GlobalCertificateDerivation deriveGlobal(InspectionId inspectionId) {
        Inspection inspection = inspections.require(inspectionId);
        List<Certificate> partials = certificates.findByBackingInspection(inspectionId).stream()
                .filter(certificate -> certificate.scope().coveredSubsystem().isPresent())
                .toList();
        return globalPolicy.deriveFrom(
                new GlobalDerivationContext(inspection, partials, clock.now()));
    }

    private IssuanceDecision issueWithin(Inspection inspection, CertificateScope scope) {
        Optional<IssuanceDecision> alreadyIssued = alreadyIssued(inspection.id(), scope);
        if (alreadyIssued.isPresent()) {
            return alreadyIssued.get();
        }
        Instant now = clock.now();
        certificates.findLatestForAsset(inspection.assetId(), scope)
                .filter(previous -> previous.status().expired() || previous.validity().expiredAt(now))
                .ifPresent(previous -> {
                    throw new DomainException("asset " + inspection.assetId() + " was certified over "
                            + scope.describe() + " by " + previous.id()
                            + ", which has expired; renew it to keep the link between both");
                });
        return create(inspection, scope, null);
    }

    private IssuanceDecision renewWithin(Inspection inspection, CertificateScope scope) {
        var existing = certificates.findByBackingInspection(inspection.id(), scope);
        if (existing.isPresent() && existing.get().previousCertificateId().isPresent()) {
            return new IssuanceDecision.AlreadyIssued(existing.get().id(), existing.get().status());
        }
        Certificate previous = certificates.findLatestForAsset(inspection.assetId(), scope)
                .orElseThrow(() -> new DomainException("asset " + inspection.assetId()
                        + " has no certificate to renew over " + scope.describe()));
        Validate.ensure(previous.validity().expiredAt(clock.now()),
                "certificate " + previous.id() + " has not expired yet, so it cannot be renewed");
        Validate.ensure(!inspection.id().equals(previous.backingInspectionId()),
                "renewal requires a new inspection");
        Validate.ensure(inspection.startedAt().filter(at -> at.isAfter(previous.validity().issuedAt())).isPresent(),
                "renewal requires an inspection started after the previous certificate was issued");
        return create(inspection, scope, previous);
    }

    private Optional<IssuanceDecision> alreadyIssued(InspectionId inspectionId, CertificateScope scope) {
        return certificates.findByBackingInspection(inspectionId, scope)
                .map(existing -> new IssuanceDecision.AlreadyIssued(existing.id(), existing.status()));
    }

    private IssuanceDecision create(Inspection inspection, CertificateScope scope, Certificate previous) {
        Optional<IssuanceDecision> alreadyIssued = alreadyIssued(inspection.id(), scope);
        if (alreadyIssued.isPresent()) {
            return alreadyIssued.get();
        }
        var blockers = policy.blockersFor(contextFor(inspection, scope));
        if (!blockers.isEmpty()) {
            return new IssuanceDecision.Blocked(blockers);
        }
        // The factory's structural safeguards hold even with additional/custom requirements.
        var at = clock.now();
        Validate.ensure(inspection.status().closed(), "the backing inspection must be closed");
        Validate.ensure(!inspection.closedAt().orElseThrow().isAfter(at), "issuance cannot precede closure");
        var validity = validityPolicy.validityFrom(at);
        Validate.ensure(validity.issuedAt().equals(at), "validity must begin at issuance");
        return new IssuanceDecision.Issued(issuer.issue(new CertificateId(ids.newIdentifier()),
                inspection.assetId(), inspection.id(), inspection.requireFrozenSchemaVersionId(),
                scope, validity, previous == null ? null : previous.id()));
    }

    private CertificationContext contextFor(Inspection inspection, CertificateScope scope) {
        Instant now = clock.now();
        var liveCertificate = certificates.findNonExpiredForAsset(inspection.assetId(), scope)
                .filter(certificate -> certificate.coversMoment(now))
                .filter(certificate -> !certificate.backingInspectionId().equals(inspection.id()))
                .map(Certificate::id);
        return new CertificationContext(inspection, findings.findingsOf(inspection.id()), clock.today(),
                scope, laterClosedInspectionOf(inspection), liveCertificate);
    }

    private void requireWholeAssetSchema(Inspection inspection) {
        Validate.ensure(inspection.certifiableSubsystems().isEmpty(),
                "inspection " + inspection.id() + " certifies the subsystems "
                        + inspection.certifiableSubsystems() + " separately, so derive the global "
                        + "certificate from them instead of issuing one");
    }

    private void requireDeclaredSubsystem(Inspection inspection, Subsystem subsystem) {
        Validate.required(subsystem, "subsystem");
        Validate.ensure(inspection.certifiableSubsystems().contains(subsystem),
                "inspection " + inspection.id() + " does not certify subsystem " + subsystem
                        + "; it certifies " + inspection.certifiableSubsystems());
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
