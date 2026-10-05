package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public final class Asset {

    private final AssetId id;
    private final JurisdictionId jurisdiction;
    private final String name;
    private final AssetType assetType;
    private final Map<String, String> characteristics;
    private final Set<Subsystem> subsystems;
    private ResponsiblePartyRef responsible;
    private String location;

    public Asset(AssetId id, String name, AssetType assetType, Map<String, String> characteristics,
            ResponsiblePartyRef responsible, String location, Set<Subsystem> subsystems, JurisdictionId jurisdiction) {
        this.jurisdiction = Validate.required(jurisdiction, "jurisdiction");
        this.id = Validate.required(id, "asset id");
        this.name = Validate.requiredText(name, "asset name");
        this.assetType = Validate.required(assetType, "asset type id");
        this.characteristics = assetType.validateCharacteristics(characteristics);
        this.subsystems = assetType.validateSubsystems(subsystems);
        this.responsible = Validate.required(responsible, "asset responsible");
        this.location = Validate.requiredText(location, "asset location");
    }

    public Asset(AssetId id, String name, AssetType assetType, Map<String, String> characteristics,
            ResponsiblePartyRef responsible, String location, JurisdictionId jurisdiction) {
        this(id, name, assetType, characteristics, responsible, location,
                Validate.required(assetType, "asset type id").subsystems(), jurisdiction);
    }

    public JurisdictionId jurisdiction() { return jurisdiction; }

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

    public Set<Subsystem> subsystems() {
        return subsystems;
    }

    public ResponsiblePartyRef responsible() {
        return responsible;
    }

    public String location() {
        return location;
    }

    public void assignResponsible(ResponsiblePartyRef newResponsible) {
        Validate.required(newResponsible, "new responsible");
        Validate.ensure(!newResponsible.equals(responsible),
                "asset " + id + " is already the responsibility of " + newResponsible);
        this.responsible = newResponsible;
    }

    public void relocate(String newLocation) {
        Validate.requiredText(newLocation, "new location");
        Validate.ensure(!newLocation.equals(location),
                "asset " + id + " is already located at " + newLocation);
        this.location = newLocation;
    }

    public AssetSnapshot captureSnapshot(Instant capturedAt) {
        return new AssetSnapshot(id, assetType, name, characteristics, location, responsible,
                subsystems, capturedAt, jurisdiction);
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
