package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaDraft {

    private final List<Section> sections = new ArrayList<>();

    public SchemaDraft() {
    }

    public SchemaDraft(SchemaVersion seed) {
        Validate.required(seed, "seed version");
        sections.addAll(seed.sections());
    }

    public void addSection(Section section) {
        Validate.required(section, "section");
        Validate.ensure(sections.stream().noneMatch(existing -> existing.name().equals(section.name())),
                "section '" + section.name() + "' is already part of the draft");
        sections.add(section);
    }

    public void removeSection(String name) {
        sections.removeIf(section -> section.name().equals(name));
    }

    public List<Section> sections() {
        return sections.stream().sorted(Comparator.comparingInt(Section::order)).toList();
    }

    public List<Criterion> criteria() {
        return sections.stream().flatMap(section -> section.criteria().stream()).toList();
    }

    public List<String> publicationViolations() {
        List<String> violations = new ArrayList<>();
        violations.addAll(contentIsPresent());
        violations.addAll(criterionIdsAreUnique());
        violations.addAll(everyRuleIsTotal());
        return violations;
    }

    private List<String> contentIsPresent() {
        List<String> violations = new ArrayList<>();
        if (sections.isEmpty()) {
            violations.add("a version must declare at least one section");
        }
        if (criteria().isEmpty()) {
            violations.add("a version must declare at least one criterion");
        }
        return violations;
    }

    private List<String> criterionIdsAreUnique() {
        Set<CriterionId> seen = new HashSet<>();
        return criteria().stream()
                .map(Criterion::id)
                .filter(id -> !seen.add(id))
                .distinct()
                .map(id -> "criterion " + id + " is declared more than once")
                .toList();
    }

    private List<String> everyRuleIsTotal() {
        return criteria().stream()
                .flatMap(criterion -> criterion.rule().publicationViolations().stream()
                        .map(violation -> "criterion " + criterion.id() + ": " + violation))
                .toList();
    }

}
