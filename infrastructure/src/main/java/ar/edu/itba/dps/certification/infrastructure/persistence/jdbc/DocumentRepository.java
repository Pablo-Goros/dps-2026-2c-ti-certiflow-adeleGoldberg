package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.infrastructure.persistence.StaleAggregateException;
import ar.edu.itba.dps.certification.infrastructure.persistence.codec.StateCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared mechanics of the aggregate-per-row repositories.
 *
 * <p>Every table has the same shape: {@code seq} (insertion order, which is the order the
 * in-memory repositories returned), {@code id}, {@code row_version}, one {@code doc} column holding
 * the whole aggregate and a few indexed columns derived from it for the queries. The indexed
 * columns are a projection rewritten on every save; the document is the source of truth.
 */
abstract class DocumentRepository<A> {

    private final JdbcTransactions db;
    private final StateCodec codec;
    private final Class<A> type;
    private final String table;

    DocumentRepository(JdbcTransactions db, StateCodec codec, Class<A> type, String table) {
        this.db = db;
        this.codec = codec;
        this.type = type;
        this.table = table;
    }

    /** Inserts or updates the aggregate together with its indexed columns. */
    protected void store(String id, A aggregate, Map<String, Object> indexed) {
        db.execute(() -> write(id, aggregate, indexed));
    }

    private void write(String id, A aggregate, Map<String, Object> indexed) {
        String document = codec.write(aggregate);
        JdbcTransactions.Tracked known = db.trackedByInstance(aggregate);
        if (known != null && known.table.equals(table) && known.id.equals(id)) {
            int updated = db.update(updateSql(indexed, true), parameters(document, indexed, id, known.version));
            if (updated == 0) {
                throw new StaleAggregateException(table, id);
            }
            known.version++;
            return;
        }
        int updated = db.update(updateSql(indexed, false), parameters(document, indexed, id));
        if (updated > 0) {
            long version = db.query("SELECT row_version FROM " + table + " WHERE id = ?",
                    row -> row.getLong(1), id).getFirst();
            db.track(table, id, aggregate, version);
            return;
        }
        db.update(insertSql(indexed), insertParameters(id, indexed, document));
        db.track(table, id, aggregate, 0L);
    }

    protected Optional<A> findOne(String clause, Object... parameters) {
        return load(clause, true, parameters).stream().findFirst();
    }

    protected List<A> findMany(String clause, Object... parameters) {
        return load(clause, false, parameters);
    }

    protected Optional<A> findByKey(String id) {
        JdbcTransactions.Tracked known = db.trackedByKey(table, id);
        if (known != null) {
            return Optional.of(type.cast(known.aggregate));
        }
        return findOne("WHERE t.id = ?", id);
    }

    protected JdbcTransactions db() {
        return db;
    }

    protected static Map<String, Object> columns() {
        return new LinkedHashMap<>();
    }

    private List<A> load(String clause, boolean onlyFirst, Object[] parameters) {
        String sql = "SELECT t.id, t.row_version, t.doc FROM " + table + " t " + clause + " ORDER BY t.seq"
                + (onlyFirst ? " FETCH FIRST 1 ROWS ONLY" : "");
        List<Stored> rows = db.query(sql, row -> new Stored(row.getString(1), row.getLong(2), row.getString(3)),
                parameters);
        List<A> result = new ArrayList<>(rows.size());
        for (Stored stored : rows) {
            JdbcTransactions.Tracked known = db.trackedByKey(table, stored.id());
            if (known != null) {
                result.add(type.cast(known.aggregate));
                continue;
            }
            A aggregate = codec.read(stored.document(), type);
            db.track(table, stored.id(), aggregate, stored.version());
            result.add(aggregate);
        }
        return result;
    }

    private String updateSql(Map<String, Object> indexed, boolean checkVersion) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table)
                .append(" SET row_version = row_version + 1, doc = ?");
        indexed.keySet().forEach(column -> sql.append(", ").append(column).append(" = ?"));
        sql.append(" WHERE id = ?");
        if (checkVersion) {
            sql.append(" AND row_version = ?");
        }
        return sql.toString();
    }

    private String insertSql(Map<String, Object> indexed) {
        StringBuilder names = new StringBuilder("id, row_version, doc");
        StringBuilder marks = new StringBuilder("?, 0, ?");
        indexed.keySet().forEach(column -> {
            names.append(", ").append(column);
            marks.append(", ?");
        });
        return "INSERT INTO " + table + " (" + names + ") VALUES (" + marks + ")";
    }

    private static Object[] parameters(String document, Map<String, Object> indexed, Object... tail) {
        List<Object> values = new ArrayList<>();
        values.add(document);
        values.addAll(indexed.values());
        values.addAll(List.of(tail));
        return values.toArray();
    }

    private static Object[] insertParameters(String id, Map<String, Object> indexed, String document) {
        List<Object> values = new ArrayList<>();
        values.add(id);
        values.add(document);
        values.addAll(indexed.values());
        return values.toArray();
    }

    private record Stored(String id, long version, String document) {
    }
}
