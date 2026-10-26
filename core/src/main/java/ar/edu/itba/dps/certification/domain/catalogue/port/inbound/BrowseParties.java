package ar.edu.itba.dps.certification.domain.catalogue.port.inbound;

import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

/** Input contract implemented by the application use case. */
public interface BrowseParties {
    List<Party> all();

    Optional<Party> find(PartyId id);
}
