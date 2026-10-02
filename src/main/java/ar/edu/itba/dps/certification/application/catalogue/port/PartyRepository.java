package ar.edu.itba.dps.certification.application.catalogue.port;

import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.PartyId;

import java.util.List;
import java.util.Optional;

public interface PartyRepository {

    void save(Party party);

    Optional<Party> findById(PartyId id);

    default Party require(PartyId id) {
        return findById(id).orElseThrow(() -> new DomainException("party " + id + " is not registered"));
    }

    List<Party> findAll();
}
