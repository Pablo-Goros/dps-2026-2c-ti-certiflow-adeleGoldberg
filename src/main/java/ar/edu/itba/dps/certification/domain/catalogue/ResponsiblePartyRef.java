package ar.edu.itba.dps.certification.domain.catalogue;

import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.Validate;

public record ResponsiblePartyRef(PartyId partyId, String displayName) {

    public ResponsiblePartyRef {
        Validate.required(partyId, "responsible party id");
        displayName = Validate.requiredText(displayName, "responsible party display name");
    }

    @Override
    public String toString() {
        return displayName + " (" + partyId + ")";
    }
}
