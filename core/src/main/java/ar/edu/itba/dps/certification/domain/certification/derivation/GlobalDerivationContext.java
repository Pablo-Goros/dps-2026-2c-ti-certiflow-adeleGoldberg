package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.AssetSnapshot;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Current asset facts and the latest certificate of each subsystem, regardless of inspection. */
public record GlobalDerivationContext(AssetSnapshot asset, List<Certificate> partials, Instant at) {
    public GlobalDerivationContext {
        Validate.required(asset, "asset snapshot");
        partials = List.copyOf(Validate.required(partials, "partial certificates"));
        Validate.required(at, "instant");
        Validate.ensure(partials.stream().allMatch(c -> c.scope().coveredSubsystem().isPresent()),
                "a global certificate is derived from partial certificates only");
        Validate.ensure(partials.stream().allMatch(c -> c.assetId().equals(asset.assetId())
                && c.policy().reference().jurisdiction().equals(asset.jurisdiction())),
                "partial provenance must match the asset and jurisdiction");
        Validate.ensure(partials.stream().allMatch(c -> asset.subsystems().contains(c.scope().coveredSubsystem().orElseThrow())),
                "partial certificates must cover subsystems of the asset");
        Validate.ensure(partials.stream().map(Certificate::scope).distinct().count() == partials.size(),
                "two partial certificates cannot cover the same subsystem");
    }
    public AssetId assetId() { return asset.assetId(); }
    public Set<Subsystem> subsystemsToCover() { return asset.subsystems(); }
    public Map<Subsystem, Certificate> partialsBySubsystem() {
        Map<Subsystem, Certificate> indexed = new LinkedHashMap<>();
        for (Certificate certificate : partials) indexed.put(certificate.scope().coveredSubsystem().orElseThrow(), certificate);
        return Collections.unmodifiableMap(indexed);
    }
}
