package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Map;
import java.util.Set;

public enum AssetType {
    LABORATORY(Set.of("room", "biosafetyLevel")),
    FACTORY(Set.of("room", "productionLine")),
    FACILITY(Set.of("room", "purpose")),
    EQUIPMENT(Set.of("room", "brand", "model", "serialNumber"));

    private final Set<String> characteristicNames;
    AssetType(Set<String> characteristicNames) { this.characteristicNames = characteristicNames; }
    public Set<String> characteristicNames() { return characteristicNames; }
    public Map<String, String> validateCharacteristics(Map<String, String> values) {
        Map<String, String> validated = Validate.requiredTextEntries(values, "characteristic");
        Validate.ensure(characteristicNames.containsAll(validated.keySet()),
                "characteristics for " + this + " must be predefined: " + characteristicNames);
        return validated;
    }
}
