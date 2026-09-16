package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryAuditTrail implements AuditTrail {

    private final List<AuditEntry> entries = new ArrayList<>();

    @Override
    public void append(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> entriesFor(AuditedElementRef element) {
        return entries.stream().filter(entry -> entry.element().equals(element)).toList();
    }

    @Override
    public List<AuditEntry> entriesFor(AuditedElementRef element, AuditAction action) {
        return entriesFor(element).stream().filter(entry -> entry.action() == action).toList();
    }

    @Override
    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }

    public List<AuditEntry> withAction(AuditAction action) {
        return entries.stream().filter(entry -> entry.action() == action).toList();
    }
}
