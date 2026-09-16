package ar.edu.itba.dps.certification.domain.certification.suspension;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

public final class SuspensionRecord {

    private final SuspensionCause cause;
    private final Instant raisedAt;
    private Instant resolvedAt;
    private String resolution;

    public SuspensionRecord(SuspensionCause cause, Instant raisedAt) {
        this.cause = Validate.required(cause, "suspension cause");
        this.raisedAt = Validate.required(raisedAt, "suspension instant");
    }

    public SuspensionCause cause() {
        return cause;
    }

    public Instant raisedAt() {
        return raisedAt;
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public Optional<String> resolution() {
        return Optional.ofNullable(resolution);
    }

    public boolean unresolved() {
        return resolvedAt == null;
    }

    public void resolve(String how, Instant at) {
        if (resolvedAt != null) {
            return;
        }
        this.resolution = Validate.requiredText(how, "resolution");
        this.resolvedAt = Validate.required(at, "resolution instant");
    }
}
