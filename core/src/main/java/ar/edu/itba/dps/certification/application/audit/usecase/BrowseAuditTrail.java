package ar.edu.itba.dps.certification.application.audit.usecase;

import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.List;

/** Read side of the append-only audit trail. */
public final class BrowseAuditTrail implements ar.edu.itba.dps.certification.domain.audit.port.inbound.BrowseAuditTrail {
    private final Transactions transactions;

    private final AuditTrail trail;

    public BrowseAuditTrail(AuditTrail trail, Transactions transactions) {
        this.transactions = transactions;
        this.trail = trail;
    }

    public List<AuditEntry> all() {
        return transactions.execute(() -> {
            return trail.all();
        });
    }

    public List<AuditEntry> historyOf(AuditedElementRef element) {
        return transactions.execute(() -> {
            return trail.entriesFor(element);
        });
    }
}
