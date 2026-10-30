package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Everything the assistant sees: no ids of the inspection or the asset, no people, no names. */
public record AssistanceRequest(AssetType assetType, Set<Subsystem> subsystems, List<CriterionContext> criteria,
        List<Observation> observations) {

    public AssistanceRequest {
        Validate.required(assetType, "asset type");
        subsystems = Collections.unmodifiableSet(new LinkedHashSet<>(Validate.required(subsystems, "asset subsystems")));
        criteria = List.copyOf(Validate.requiredNonEmpty(criteria, "criteria"));
        observations = List.copyOf(Validate.requiredNonEmpty(observations, "observations"));
    }

    public Optional<CriterionContext> criterion(CriterionId criterionId) {
        return criteria.stream().filter(criterion -> criterion.id().equals(criterionId)).findFirst();
    }
}
