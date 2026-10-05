package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public enum AssetType {
    LABORATORY(Set.of("room", "biosafetyLevel"), Set.of()),
    FACTORY(Set.of("room", "productionLine"),
            subsystems("electrical installation", "pressure system")),
    FACILITY(Set.of("room", "purpose"),
            subsystems("electrical installation", "pressure system", "building safety")),
    EQUIPMENT(Set.of("room", "brand", "model", "serialNumber"), Set.of());

    private final Set<String> characteristicNames;
    private final Set<Subsystem> subsystems;

    AssetType(Set<String> characteristicNames, Set<Subsystem> subsystems) {
        this.characteristicNames = characteristicNames;
        this.subsystems = subsystems;
    }

    public Set<String> characteristicNames() {
        return characteristicNames;
    }

    public Set<Subsystem> subsystems() {
        return subsystems;
    }

    public boolean dividesIntoSubsystems() {
        return !subsystems.isEmpty();
    }

    public Map<String, String> validateCharacteristics(Map<String, String> values) {
        Map<String, String> validated = Validate.requiredTextEntries(values, "characteristic");
        Validate.ensure(characteristicNames.containsAll(validated.keySet()),
                "characteristics for " + this + " must be predefined: " + characteristicNames);
        return validated;
    }

    public Set<Subsystem> validateSubsystems(Set<Subsystem> declared) {
        Validate.required(declared, "asset subsystems");
        Validate.ensure(dividesIntoSubsystems() || declared.isEmpty(),
                "assets of type " + this + " are certified as a whole and declare no subsystem");
        Validate.ensure(subsystems.containsAll(declared),
                "subsystems of " + this + " must be predefined: " + subsystems);
        return orderedAsDeclaredByTheType(declared);
    }

    private Set<Subsystem> orderedAsDeclaredByTheType(Set<Subsystem> declared) {
        Set<Subsystem> ordered = new LinkedHashSet<>();
        subsystems.stream().filter(declared::contains).forEach(ordered::add);
        return Collections.unmodifiableSet(ordered);
    }

    private static Set<Subsystem> subsystems(String... names) {
        Set<Subsystem> declared = new LinkedHashSet<>();
        for (String name : names) {
            declared.add(Subsystem.of(name));
        }
        return Collections.unmodifiableSet(declared);
    }
}
