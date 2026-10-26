package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;

/** Input contract implemented by the application use case. */
public interface RegisterParty {
    Party register(String name, PartyKind kind);
}
