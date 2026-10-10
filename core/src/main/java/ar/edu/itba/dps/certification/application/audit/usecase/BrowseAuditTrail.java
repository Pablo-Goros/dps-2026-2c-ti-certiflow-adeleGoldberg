package ar.edu.itba.dps.certification.application.audit.usecase;

import ar.edu.itba.dps.certification.application.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;

import java.util.List;

/** Read side of the append-only audit trail. */
public final class BrowseAuditTrail {

    private final AuditTrail trail;

    public BrowseAuditTrail(AuditTrail trail) {
        this.trail = trail;
    }

    public List<AuditEntry> all() {
        return trail.all();
    }

    public List<AuditEntry> historyOf(AuditedElementRef element) {
        return trail.entriesFor(element);
    }
}
