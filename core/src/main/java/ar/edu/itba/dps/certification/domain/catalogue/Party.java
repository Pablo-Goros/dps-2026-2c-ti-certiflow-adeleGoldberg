package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public final class Party {

    private final PartyId id;
    private final String name;
    private final PartyKind kind;

    public Party(PartyId id, String name, PartyKind kind) {
        this.id = Validate.required(id, "party id");
        this.name = Validate.requiredText(name, "party name");
        this.kind = Validate.required(kind, "party kind");
    }

    public PartyId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public PartyKind kind() {
        return kind;
    }

    /** An inspection is carried out and signed by a person; an organization cannot be its inspector. */
    public PartyId asInspector() {
        Validate.ensure(kind == PartyKind.PERSON,
                "party " + id + " is an organization and cannot be assigned as inspector");
        return id;
    }

    public ResponsiblePartyRef reference() {
        return new ResponsiblePartyRef(id, name);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Party that && id.equals(that.id);
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
