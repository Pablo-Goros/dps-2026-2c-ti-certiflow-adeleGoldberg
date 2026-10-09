package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.application.audit.port.AuditTrail;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditEntry;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.List;

/** Append-only: entries are never updated or deleted, so there is no version and no identity map. */
public final class JdbcAuditTrail implements AuditTrail {

    private static final String SELECT = "SELECT doc FROM audit_entry ";

    private final JdbcTransactions db;
    private final StateCodec codec;

    public JdbcAuditTrail(JdbcTransactions db, StateCodec codec) {
        this.db = db;
        this.codec = codec;
    }

    @Override
    public void append(AuditEntry entry) {
        db.update("INSERT INTO audit_entry (element_type, element_id, action, doc) VALUES (?, ?, ?, ?)",
                entry.element().type().name(), entry.element().id(), entry.action().name(), codec.write(entry));
    }

    @Override
    public List<AuditEntry> entriesFor(AuditedElementRef element) {
        return read(SELECT + "WHERE element_type = ? AND element_id = ? ORDER BY seq",
                element.type().name(), element.id());
    }

    @Override
    public List<AuditEntry> entriesFor(AuditedElementRef element, AuditAction action) {
        return read(SELECT + "WHERE element_type = ? AND element_id = ? AND action = ? ORDER BY seq",
                element.type().name(), element.id(), action.name());
    }

    @Override
    public List<AuditEntry> all() {
        return read(SELECT + "ORDER BY seq");
    }

    private List<AuditEntry> read(String sql, Object... parameters) {
        return db.query(sql, row -> codec.read(row.getString(1), AuditEntry.class), parameters);
    }
}
