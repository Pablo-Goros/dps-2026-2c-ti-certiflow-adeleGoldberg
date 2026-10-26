package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.List;
import java.util.Optional;

/** Read side of the registered parties, so driving adapters never reach for the repository. */
public final class BrowseParties implements ar.edu.itba.dps.certification.domain.catalogue.port.inbound.BrowseParties {
    private final Transactions transactions;

    private final PartyRepository parties;

    public BrowseParties(PartyRepository parties, Transactions transactions) {
        this.transactions = transactions;
        this.parties = parties;
    }

    public List<Party> all() {
        return transactions.execute(() -> {
            return parties.findAll();
        });
    }

    public Optional<Party> find(PartyId id) {
        return transactions.execute(() -> {
            return parties.findById(id);
        });
    }
}
