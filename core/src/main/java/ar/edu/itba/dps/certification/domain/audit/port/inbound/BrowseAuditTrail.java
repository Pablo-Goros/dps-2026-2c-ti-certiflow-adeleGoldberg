package ar.edu.itba.dps.certification.domain.audit.port.inbound;

import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface BrowseAuditTrail {
    List<AuditEntry> all();

    List<AuditEntry> historyOf(AuditedElementRef element);
}
