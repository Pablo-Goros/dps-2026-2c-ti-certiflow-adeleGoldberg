package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SchemaApplicability {

    public InspectionSchema create(SchemaId id, String name, Set<AssetType> types,
            Set<AssetType> alreadyCoveredAssetTypes) {
        Set<AssetType> covered = Validate.required(alreadyCoveredAssetTypes, "already covered asset types");
        Validate.requiredNonEmpty(types, "applicable asset types")
                .forEach(type -> requireAvailable(type, covered));
        return new InspectionSchema(id, name, types);
    }

    public void applyTo(InspectionSchema schema, AssetType type, Optional<SchemaId> registeredOwner) {
        requireAvailable(type, Validate.required(registeredOwner, "registered schema owner"));
        requireVersionEvaluatesEveryPartOf(schema, type);
        schema.applyTo(type);
    }

    public void stopApplyingTo(InspectionSchema schema, AssetType type) {
        Validate.required(type, "asset type");
        Validate.ensure(schema.appliesTo(type), "schema does not apply to asset type " + type);
        throw new DomainException(
                "removing applicability would leave the asset type without a schema; transfer it instead");
    }

    public void transfer(InspectionSchema source, InspectionSchema target, AssetType type,
            Optional<SchemaId> registeredOwner) {
        Optional<SchemaId> owner = Validate.required(registeredOwner, "registered schema owner");
        Validate.required(type, "asset type");
        Validate.ensure(!source.id().equals(target.id()), "applicability must transfer to another schema");
        Validate.ensure(source.appliesTo(type), "source schema does not apply to asset type " + type);
        Validate.ensure(source.applicableAssetTypes().size() > 1,
                "source schema must remain applicable to at least one asset type");
        Validate.ensure(!target.appliesTo(type), "target schema already applies to asset type " + type);
        Validate.ensure(owner.isPresent() && owner.get().equals(source.id()),
                "source schema must be the registered owner of the asset type");
        Validate.ensure(target.latestPublishedVersion().isPresent(),
                "the replacement schema must have a published version");
        requireVersionEvaluatesEveryPartOf(target, type);
        source.stopApplyingTo(type);
        target.applyTo(type);
    }

    private void requireVersionEvaluatesEveryPartOf(InspectionSchema schema, AssetType type) {
        schema.latestPublishedVersion().ifPresent(version -> {
            Set<Subsystem> unevaluated = type.subsystems().stream()
                    .filter(subsystem -> !version.declaredSubsystems().contains(subsystem))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Validate.ensure(unevaluated.isEmpty(), "version " + version.id() + " of schema "
                    + schema.id() + " evaluates no criterion for " + unevaluated
                    + ", which assets of type " + type + " may have");
        });
    }

    private void requireAvailable(AssetType type, Set<AssetType> alreadyCoveredAssetTypes) {
        Validate.required(type, "asset type");
        Validate.ensure(!alreadyCoveredAssetTypes.contains(type),
                "asset type " + type + " is already covered by a schema");
    }

    private void requireAvailable(AssetType type, Optional<SchemaId> registeredOwner) {
        Validate.required(type, "asset type");
        Validate.ensure(registeredOwner.isEmpty(),
                "asset type " + type + " is already covered by a schema");
    }
}
