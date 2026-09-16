package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record AssetId(String value) {

    public AssetId {
        value = Validate.requiredText(value, "asset id");
    }

    public static AssetId of(String value) {
        return new AssetId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
