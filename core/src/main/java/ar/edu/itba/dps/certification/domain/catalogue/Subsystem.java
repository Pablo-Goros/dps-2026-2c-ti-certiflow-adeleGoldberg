package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record Subsystem(String name) {

    public Subsystem {
        name = Validate.requiredText(name, "subsystem name");
    }

    public static Subsystem of(String name) {
        return new Subsystem(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
