package ar.edu.itba.dps.certification.application.audit.port;

import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;

import java.util.List;

public interface AuditTrail {

    void append(AuditEntry entry);

    List<AuditEntry> entriesFor(AuditedElementRef element);

    List<AuditEntry> entriesFor(AuditedElementRef element, AuditAction action);

    List<AuditEntry> all();
}
