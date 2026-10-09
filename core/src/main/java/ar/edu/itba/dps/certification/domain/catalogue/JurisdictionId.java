package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record JurisdictionId(String value) {
    public JurisdictionId { value = Validate.requiredText(value, "jurisdiction id"); }
    public static JurisdictionId of(String value) { return new JurisdictionId(value); }
    @Override public String toString() { return value; }
}
