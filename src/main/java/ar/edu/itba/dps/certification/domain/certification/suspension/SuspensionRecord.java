package ar.edu.itba.dps.certification.domain.certification.suspension;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Optional;

public record SuspensionRecord (SuspensionCause cause, Instant raisedAt, Optional<Instant> resolvedAt, Optional<String> resolution) {

    public SuspensionRecord(SuspensionCause cause, Instant raisedAt) {
        this(cause, raisedAt, Optional.empty(), Optional.empty());
    }

    public SuspensionRecord {
        Validate.required(cause, "suspension cause");
        Validate.required(raisedAt, "suspension instant");
        Validate.required(resolvedAt, "resolution instant");
        Validate.required(resolution, "resolution");
    }

    public boolean unresolved() {
        return resolvedAt.isEmpty();
    }

    public SuspensionRecord resolved(String how, Instant at) {
        if (!unresolved()) {
            return this;
        }
        return new SuspensionRecord(
                cause,
                raisedAt,
                Optional.of(Validate.required(at, "resolution instant")),
                Optional.of(Validate.requiredText(how, "resolution")));
    }
}
