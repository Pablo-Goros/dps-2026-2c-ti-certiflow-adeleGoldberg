package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

public record PublicationResult(Optional<SchemaVersion> version, List<String> violations) {

    public PublicationResult {
        Validate.required(version, "version");
        violations = List.copyOf(Validate.required(violations, "violations"));
        Validate.ensure(version.isPresent() == violations.isEmpty(),
                "a publication either yields a version or reports why it was refused");
    }

    public static PublicationResult published(SchemaVersion version) {
        return new PublicationResult(Optional.of(version), List.of());
    }

    public static PublicationResult refused(List<String> violations) {
        return new PublicationResult(Optional.empty(), violations);
    }

    public boolean published() {
        return version.isPresent();
    }

    public SchemaVersion publishedVersion() {
        return version.orElseThrow(() -> new DomainException(
                "the draft was refused: " + String.join("; ", violations)));
    }
}
