package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;
import java.util.Optional;

public final class JdbcPartyRepository extends DocumentRepository<Party> implements PartyRepository {

    public JdbcPartyRepository(JdbcTransactions db, StateCodec codec) {
        super(db, codec, Party.class, "party");
    }

    @Override
    public void save(Party party) {
        store(party.id().value(), party, columns());
    }

    @Override
    public Optional<Party> findById(PartyId id) {
        return findByKey(id.value());
    }

    @Override
    public List<Party> findAll() {
        return findMany("");
    }
}
