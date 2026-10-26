package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.policy.CertificationPolicySnapshot;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionRecord;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.List;
import java.util.Optional;

public final class Certificate {

    private final CertificateId id;
    private final CertificationPolicySnapshot policy;
    private final CertificateMode mode;
    private final AssetId assetId;
    private final InspectionId backingInspectionId;
    private final SchemaVersionId schemaVersionId;
    private final CertificateScope scope;
    private final ValidityPeriod validity;
    private final CertificateId previousCertificateId;
    private final List<SuspensionRecord> suspensions = new ArrayList<>();
    private CertificateStatus status = CertificateStatus.VALID;

    Certificate(CertificateId id, Inspection inspection, CertificationAssessment assessment, Certificate previous) {
        this.id = Validate.required(id, "certificate id");
        Validate.required(assessment, "assessment").requireEligibleFor(inspection);
        Validate.ensure(inspection.status().closed(), "the backing inspection must be closed");
        this.scope = assessment.scope();
        Validate.ensure(scope.coveredSubsystem().map(inspection.certifiableSubsystems()::contains)
                .orElseGet(() -> inspection.certifiableSubsystems().isEmpty()),
                "assessment scope must be certified by the inspection");
        this.policy = assessment.policy();
        inspection.assetSnapshot().ifPresent(asset -> Validate.ensure(
                asset.jurisdiction().equals(policy.reference().jurisdiction()),
                "assessment policy must match the inspection jurisdiction"));
        this.mode = assessment.mode().orElseThrow();
        this.assetId = inspection.assetId();
        this.backingInspectionId = inspection.id();
        this.schemaVersionId = inspection.requireFrozenSchemaVersionId();
        this.validity = policy.validityFrom(assessment.evaluatedAt(), mode);
        if (previous != null) {
            Validate.ensure(!id.equals(previous.id()), "certificate " + id + " cannot succeed itself");
            Validate.ensure(assetId.equals(previous.assetId()) && scope.equals(previous.scope()),
                    "renewal must cover the same asset and scope");
            Validate.ensure(previous.validity().expiredAt(validity.issuedAt()),
                    "the previous certificate must be expired before renewal");
            Validate.ensure(!backingInspectionId.equals(previous.backingInspectionId()), "renewal requires a new inspection");
            Validate.ensure(inspection.startedAt().orElseThrow().isAfter(previous.validity().issuedAt()),
                    "renewal requires an inspection started after the previous certificate was issued");
        }
        this.previousCertificateId = previous == null ? null : previous.id();
    }

    public CertificationPolicySnapshot policy() { return policy; }
    public CertificateMode mode() { return mode; }

    public CertificateId id() {
        return id;
    }

    public AssetId assetId() {
        return assetId;
    }

    public InspectionId backingInspectionId() {
        return backingInspectionId;
    }

    public SchemaVersionId schemaVersionId() {
        return schemaVersionId;
    }

    public CertificateScope scope() {
        return scope;
    }

    public boolean covers(CertificateScope candidate) {
        return scope.equals(candidate);
    }

    public ValidityPeriod validity() {
        return validity;
    }

    public CertificateStatus status() {
        return status;
    }

    public Optional<CertificateId> previousCertificateId() {
        return Optional.ofNullable(previousCertificateId);
    }

    public List<SuspensionRecord> suspensions() {
        return List.copyOf(suspensions);
    }

    public List<SuspensionCause> unresolvedCauses() {
        return suspensions.stream()
                .filter(SuspensionRecord::unresolved)
                .map(SuspensionRecord::cause)
                .toList();
    }

    boolean suspend(SuspensionCause cause, Instant at) {
        Validate.required(cause, "suspension cause");
        Validate.required(at, "suspension instant");
        if (expiredAt(at)) {
            return false;
        }
        if (hasRecorded(cause)) {
            return false;
        }
        suspensions.add(new SuspensionRecord(cause, at));
        status = CertificateStatus.SUSPENDED;
        return true;
    }

    boolean resolveCause(SuspensionCause cause, String how, Instant at) {
        Validate.required(cause, "suspension cause");
        return resolveCauses(candidate -> candidate.equals(cause), how, at);
    }

    boolean resolveCauses(Predicate<SuspensionCause> matches, String how, Instant at) {
        Validate.required(matches, "cause matcher");
        Validate.requiredText(how, "resolution");
        Validate.required(at, "resolution instant");

        suspensions.replaceAll(record ->
                (record.unresolved() && matches.test(record.cause()))
                        ? record.resolved(how, at)
                        : record
        );

        return reactivateIfFullyResolved(at);
    }

    boolean reactivateIfFullyResolved(Instant at) {
        Validate.required(at, "instant");
        if (status != CertificateStatus.SUSPENDED) {
            return false;
        }
        if (!unresolvedCauses().isEmpty()) {
            return false;
        }
        if (expiredAt(at)) {
            return false;
        }
        status = CertificateStatus.VALID;
        return true;
    }

    public boolean expireIfDue(Instant at) {
        Validate.required(at, "instant");
        if (status.expired() || !validity.expiredAt(at)) {
            return false;
        }
        status = CertificateStatus.EXPIRED;
        return true;
    }

    public boolean coversMoment(Instant moment) {
        return !status.expired() && validity.coversMoment(moment);
    }

    public boolean inForceAt(Instant moment) {
        return status == CertificateStatus.VALID && validity.coversMoment(moment);
    }

    private boolean expiredAt(Instant at) {
        return status.expired() || validity.expiredAt(at);
    }

    private boolean hasRecorded(SuspensionCause cause) {
        return suspensions.stream()
                .anyMatch(record -> record.cause().equals(cause));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Certificate that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Certificate " + id + " over " + scope.describe() + " (" + status + ")";
    }
}
