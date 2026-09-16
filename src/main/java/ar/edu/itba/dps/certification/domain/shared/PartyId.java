package ar.edu.itba.dps.certification.domain.shared;

public record PartyId(String value) {

    public PartyId {
        value = Validate.requiredText(value, "party id");
    }

    public static PartyId of(String value) {
        return new PartyId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
