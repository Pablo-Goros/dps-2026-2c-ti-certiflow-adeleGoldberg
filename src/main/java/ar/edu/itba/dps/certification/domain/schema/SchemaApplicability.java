package ar.edu.itba.dps.certification.domain.schema;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaRepository;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Set;

public final class SchemaApplicability {
    private final SchemaRepository schemas;
    public SchemaApplicability(SchemaRepository schemas) { this.schemas = schemas; }

    public InspectionSchema create(SchemaId id, String name, Set<AssetType> types) {
        Validate.requiredNonEmpty(types, "applicable asset types").forEach(this::requireAvailable);
        return new InspectionSchema(id, name, types);
    }

    public void applyTo(InspectionSchema schema, AssetType type) {
        requireAvailable(type);
        schema.applyTo(type);
    }

    public void stopApplyingTo(InspectionSchema schema, AssetType type) {
        Validate.required(type, "asset type");
        Validate.ensure(schema.appliesTo(type), "schema does not apply to asset type " + type);
        throw new DomainException(
                "removing applicability would leave the asset type without a schema; transfer it instead");
    }

    public void transfer(InspectionSchema source, InspectionSchema target, AssetType type) {
        Validate.required(type, "asset type");
        Validate.ensure(!source.id().equals(target.id()), "applicability must transfer to another schema");
        Validate.ensure(source.appliesTo(type), "source schema does not apply to asset type " + type);
        Validate.ensure(source.applicableAssetTypes().size() > 1,
                "source schema must remain applicable to at least one asset type");
        Validate.ensure(!target.appliesTo(type), "target schema already applies to asset type " + type);
        var owner = schemas.findByApplicableAssetType(type);
        Validate.ensure(owner.isPresent() && owner.get().id().equals(source.id()),
                "source schema must be the registered owner of the asset type");
        Validate.ensure(target.latestPublishedVersion().isPresent(),
                "the replacement schema must have a published version");
        source.stopApplyingTo(type);
        target.applyTo(type);
    }

    private void requireAvailable(AssetType type) {
        Validate.required(type, "asset type");
        Validate.ensure(schemas.findByApplicableAssetType(type).isEmpty(),
                "asset type " + type + " is already covered by a schema");
    }
}
