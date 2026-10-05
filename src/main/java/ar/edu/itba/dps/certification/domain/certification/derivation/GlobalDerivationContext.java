package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GlobalDerivationContext(Inspection inspection, List<Certificate> partials, Instant at) {

    public GlobalDerivationContext {
        Validate.required(inspection, "inspection");
        partials = List.copyOf(Validate.required(partials, "partial certificates"));
        Validate.required(at, "instant");
        Validate.ensure(partials.stream().allMatch(c -> c.backingInspectionId().equals(inspection.id())),
                "every partial certificate must be backed by the inspection being derived");
        Validate.ensure(partials.stream().allMatch(c -> c.scope().coveredSubsystem().isPresent()),
                "a global certificate is derived from partial certificates only");
        Validate.ensure(partials.stream().allMatch(c -> c.assetId().equals(inspection.assetId())
                && c.schemaVersionId().equals(inspection.requireFrozenSchemaVersionId())
                && inspection.assetSnapshot().map(snapshot -> snapshot.jurisdiction().equals(c.policy().reference().jurisdiction())).orElse(false)),
                "partial provenance must match the inspection asset, schema and jurisdiction");
        Validate.ensure(partials.stream().map(Certificate::scope).distinct().count() == partials.size(),
                "two partial certificates cannot cover the same subsystem of one inspection");
    }

    public InspectionId inspectionId() {
        return inspection.id();
    }

    public AssetId assetId() {
        return inspection.assetId();
    }

    public SchemaVersionId schemaVersionId() {
        return inspection.requireFrozenSchemaVersionId();
    }

    public Set<Subsystem> subsystemsToCover() {
        return inspection.assetSnapshot()
                .map(snapshot -> snapshot.subsystems())
                .orElseGet(Set::of);
    }

    public Map<Subsystem, Certificate> partialsBySubsystem() {
        Map<Subsystem, Certificate> indexed = new LinkedHashMap<>();
        for (Certificate certificate : partials) {
            indexed.put(certificate.scope().coveredSubsystem().orElseThrow(), certificate);
        }
        return Collections.unmodifiableMap(indexed);
    }
}
