package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceRequirement;
import ar.edu.itba.dps.certification.domain.schema.evidence.EvidenceShortfall;
import ar.edu.itba.dps.certification.domain.schema.rule.EvaluationRule;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Map;

public record Criterion(
        CriterionId id,
        EvaluationRule rule,
        List<EvidenceRequirement> evidenceRequirements) {

    public Criterion {
        Validate.required(id, "criterion id");
        Validate.required(rule, "evaluation rule");
        Validate.required(evidenceRequirements, "evidence requirements");
        evidenceRequirements = List.copyOf(evidenceRequirements);
        Validate.ensure(evidenceRequirements.stream().map(EvidenceRequirement::label).distinct()
                        .count() == evidenceRequirements.size(),
                "criterion " + id + " declares two evidence requirements with the same label, "
                        + "so an attachment could not be matched to one of them");
    }

    public static Criterion of(String criterionId, EvaluationRule rule) {
        return new Criterion(CriterionId.of(criterionId), rule, List.of());
    }

    public List<EvidenceShortfall> shortfalls(Map<String, Integer> presentedCountByLabel) {
        Validate.required(presentedCountByLabel, "presented evidence counts");
        return evidenceRequirements.stream()
                .filter(requirement -> !requirement.satisfiedBy(
                        presentedCountByLabel.getOrDefault(requirement.label(), 0)))
                .map(requirement -> requirement.shortfall(
                        presentedCountByLabel.getOrDefault(requirement.label(), 0)))
                .toList();
    }
}
