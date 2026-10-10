package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

/** Read side of the registered parties, so driving adapters never reach for the repository. */
public final class BrowseParties {

    private final PartyRepository parties;

    public BrowseParties(PartyRepository parties) {
        this.parties = parties;
    }

    public List<Party> all() {
        return parties.findAll();
    }

    public Optional<Party> find(PartyId id) {
        return parties.findById(id);
    }
}
