package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record SchemaVersion(
        SchemaVersionId id,
        List<Section> sections,
        Instant publishedAt) {

    public SchemaVersion {
        Validate.required(id, "schema version id");
        Validate.requiredNonEmpty(sections, "sections");
        Validate.required(publishedAt, "publication instant");
        sections = sections.stream().sorted(Comparator.comparingInt(Section::order)).toList();
    }

    public int number() {
        return id.number();
    }

    public List<Criterion> criteria() {
        return sections.stream().flatMap(section -> section.criteria().stream()).toList();
    }

    public Optional<Criterion> findCriterion(CriterionId criterionId) {
        return criteria().stream().filter(criterion -> criterion.id().equals(criterionId)).findFirst();
    }

    public Criterion requireCriterion(CriterionId criterionId) {
        return findCriterion(criterionId).orElseThrow(() ->
                new DomainException("criterion " + criterionId + " does not belong to " + id));
    }

}
