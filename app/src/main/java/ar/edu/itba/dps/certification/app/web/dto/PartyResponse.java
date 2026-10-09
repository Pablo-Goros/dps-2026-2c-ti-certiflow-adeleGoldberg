package ar.edu.itba.dps.certification.app.web.dto;

import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;

public record PartyResponse(String id, String name, PartyKind kind) {

    public static PartyResponse of(Party party) {
        return new PartyResponse(party.id().value(), party.name(), party.kind());
    }
}
