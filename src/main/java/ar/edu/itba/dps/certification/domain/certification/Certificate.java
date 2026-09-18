package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionRecord;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class Certificate {

    private final CertificateId id;
    private final AssetId assetId;
    private final InspectionId backingInspectionId;
    private final SchemaVersionId schemaVersionId;
    private final ValidityPeriod validity;
    private final CertificateId previousCertificateId;
    private final List<SuspensionRecord> suspensions = new ArrayList<>();
    private CertificateStatus status = CertificateStatus.VALID;

    public Certificate(CertificateId id, AssetId assetId, InspectionId backingInspectionId,
            SchemaVersionId schemaVersionId, ValidityPeriod validity,
            CertificateId previousCertificateId) {
        this.id = Validate.required(id, "certificate id");
        this.assetId = Validate.required(assetId, "asset id");
        this.backingInspectionId = Validate.required(backingInspectionId, "backing inspection id");
        this.schemaVersionId = Validate.required(schemaVersionId, "schema version id");
        this.validity = Validate.required(validity, "validity period");
        Validate.ensure(!this.id.equals(previousCertificateId),
                "certificate " + id + " cannot succeed itself");
        this.previousCertificateId = previousCertificateId;
    }

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

    public boolean suspend(SuspensionCause cause, Instant at) {
        Validate.required(cause, "suspension cause");
        Validate.required(at, "suspension instant");
        if (expiredAt(at)) {
            return false;
        }
        if (hasUnresolved(cause)) {
            return false;
        }
        suspensions.add(new SuspensionRecord(cause, at));
        status = CertificateStatus.SUSPENDED;
        return true;
    }

    public boolean resolveCause(SuspensionCause cause, String how, Instant at) {
        Validate.required(cause, "suspension cause");
        return resolveCauses(candidate -> candidate.equals(cause), how, at);
    }

    public boolean resolveCauses(Predicate<SuspensionCause> matches, String how, Instant at) {
        Validate.required(matches, "cause matcher");
        suspensions.stream()
                .filter(SuspensionRecord::unresolved)
                .filter(record -> matches.test(record.cause()))
                .forEach(record -> record.resolve(how, at));
        return reactivateIfFullyResolved(at);
    }

    public boolean reactivateIfFullyResolved(Instant at) {
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

    private boolean expiredAt(Instant at) {
        return status.expired() || validity.expiredAt(at);
    }

    private boolean hasUnresolved(SuspensionCause cause) {
        return suspensions.stream()
                .filter(SuspensionRecord::unresolved)
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
        return "Certificate " + id + " (" + status + ")";
    }
}
