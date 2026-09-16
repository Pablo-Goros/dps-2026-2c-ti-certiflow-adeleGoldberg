package ar.edu.itba.dps.certification.domain.inspection.rectification;

import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record Rectification(
        RectificationId id,
        PartyId author,
        Instant performedAt,
        String reason,
        List<RectificationChange> changes) {

    public Rectification {
        Validate.required(id, "rectification id");
        Validate.required(author, "rectification author");
        Validate.required(performedAt, "rectification instant");
        reason = Validate.requiredText(reason, "rectification reason");
        changes = Validate.requiredNonEmpty(changes, "rectification changes");
    }

    public Set<CriterionId> affectedCriteria() {
        return changes.stream()
                .map(RectificationChange::affectedCriterion)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
