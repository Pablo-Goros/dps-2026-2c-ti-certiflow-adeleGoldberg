package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record SchemaVersion(
        SchemaVersionId id,
        List<Section> sections,
        Instant publishedAt,
        Instant effectiveFrom) {

    public SchemaVersion(SchemaVersionId id, List<Section> sections, Instant publishedAt) {
        this(id, sections, publishedAt, publishedAt);
    }

    public SchemaVersion {
        Validate.required(id, "schema version id");
        Validate.requiredNonEmpty(sections, "sections");
        Validate.required(publishedAt, "publication instant");
        Validate.required(effectiveFrom, "effective from instant");
        Validate.ensure(!effectiveFrom.isBefore(publishedAt), "effectiveFrom cannot be earlier than publishedAt");
        sections = sections.stream().sorted(Comparator.comparingInt(Section::order)).toList();
        var criteria = sections.stream().flatMap(section -> section.criteria().stream()).toList();
        Validate.ensure(criteria.stream().map(Criterion::id).distinct().count() == criteria.size(),
                "a published version must contain unique criterion ids");
        Validate.ensure(criteria.stream().allMatch(c -> c.rule().publicationViolations().isEmpty()),
                "a published version must contain valid total rules");
    }

    public int number() {
        return id.number();
    }

    public List<Criterion> criteria() {
        return sections.stream().flatMap(section -> section.criteria().stream()).toList();
    }

    public Set<Subsystem> declaredSubsystems() {
        return criteria().stream()
                .map(Criterion::subsystem)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean criterionWeighsOn(CriterionId criterionId, Subsystem subsystem) {
        Validate.required(subsystem, "subsystem");
        return findCriterion(criterionId).map(criterion -> criterion.weighsOn(subsystem))
                .orElse(true);
    }

    public Optional<Criterion> findCriterion(CriterionId criterionId) {
        return criteria().stream().filter(criterion -> criterion.id().equals(criterionId)).findFirst();
    }

    public Criterion requireCriterion(CriterionId criterionId) {
        return findCriterion(criterionId).orElseThrow(() ->
                new DomainException("criterion " + criterionId + " does not belong to " + id));
    }

}
