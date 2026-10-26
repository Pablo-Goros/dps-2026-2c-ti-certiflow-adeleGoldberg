package ar.edu.itba.dps.certification.application.catalogue.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.catalogue.Party;
import ar.edu.itba.dps.certification.domain.catalogue.PartyKind;
import ar.edu.itba.dps.certification.domain.catalogue.port.PartyRepository;
import ar.edu.itba.dps.certification.domain.shared.PartyId;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

public final class RegisterParty implements ar.edu.itba.dps.certification.domain.catalogue.port.inbound.RegisterParty {
    private final Transactions transactions;

    private final PartyRepository parties;
    private final IdGenerator ids;
    private final AuditRecorder audit;

    public RegisterParty(PartyRepository parties, IdGenerator ids, AuditRecorder audit, Transactions transactions) {
        this.transactions = transactions;
        this.parties = parties;
        this.audit = audit;
        this.ids = ids;
    }

    public Party register(String name, PartyKind kind) {
        return transactions.execute(() -> {
            Party party = new Party(new PartyId(ids.newIdentifier()), name, kind);
            parties.save(party);
            audit.record(AuditedElementRef.party(party.id().value()), AuditAction.PARTY_REGISTERED,
                    AuditDetail.created(kind + " '" + party.name() + "'"));
            return party;
        });
    }
}
