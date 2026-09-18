package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.FieldChange;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Map;

public final class Asset {

    private final AssetId id;
    private final String name;
    private final AssetType assetType;
    private final Map<String, String> characteristics;
    private ResponsiblePartyRef responsible;
    private String location;

    public Asset(AssetId id, String name, AssetType assetType, Map<String, String> characteristics,
            ResponsiblePartyRef responsible, String location) {
        this.id = Validate.required(id, "asset id");
        this.name = Validate.requiredText(name, "asset name");
        this.assetType = Validate.required(assetType, "asset type id");
        this.characteristics = Validate.requiredTextEntries(characteristics, "characteristic");
        this.responsible = Validate.required(responsible, "asset responsible");
        this.location = Validate.requiredText(location, "asset location");
    }

    public AssetId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public AssetType assetType() {
        return assetType;
    }

    public Map<String, String> characteristics() {
        return characteristics;
    }

    public ResponsiblePartyRef responsible() {
        return responsible;
    }

    public String location() {
        return location;
    }

    public FieldChange assignResponsible(ResponsiblePartyRef newResponsible) {
        Validate.required(newResponsible, "new responsible");
        Validate.ensure(!newResponsible.equals(responsible),
                "asset " + id + " is already the responsibility of " + newResponsible);
        FieldChange change = FieldChange.of("responsible", responsible, newResponsible);
        this.responsible = newResponsible;
        return change;
    }

    public FieldChange relocate(String newLocation) {
        Validate.requiredText(newLocation, "new location");
        Validate.ensure(!newLocation.equals(location),
                "asset " + id + " is already located at " + newLocation);
        FieldChange change = FieldChange.of("location", location, newLocation);
        this.location = newLocation;
        return change;
    }

    public AssetSnapshot captureSnapshot(Instant capturedAt) {
        return new AssetSnapshot(id, assetType, name, characteristics, location, responsible, capturedAt);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Asset that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
