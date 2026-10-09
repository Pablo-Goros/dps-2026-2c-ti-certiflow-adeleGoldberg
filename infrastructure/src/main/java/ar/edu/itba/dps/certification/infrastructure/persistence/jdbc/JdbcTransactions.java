package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import ar.edu.itba.dps.certification.infrastructure.persistence.DuplicateKeyException;
import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Transaction boundary and the only place that talks JDBC.
 *
 * <p>{@link #execute(Supplier)} runs a unit of work on one connection: everything it writes is
 * committed together or not at all, and the exception that caused a failure is rethrown untouched
 * so business exceptions keep their type. Nested calls join the running transaction. The same unit
 * of work keeps an identity map, so within it every read of an aggregate yields the same instance
 * (the behaviour the domain and its tests were written against) and every save is checked against
 * the version that was read (optimistic locking).
 *
 * <p>Outside a unit of work each statement runs on its own connection in auto-commit mode, with
 * neither identity map nor version check.
 */
public final class JdbcTransactions {

    private static final System.Logger LOG = System.getLogger(JdbcTransactions.class.getName());
    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;
    private final ThreadLocal<UnitOfWork> running = new ThreadLocal<>();

    public JdbcTransactions(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Runs {@code work} in a transaction, or joins the one already running on this thread. */
    public <T> T execute(Supplier<T> work) {
        if (running.get() != null) {
            return work.get();
        }
        UnitOfWork unit = new UnitOfWork(open());
        running.set(unit);
        T result;
        try {
            result = work.get();
            commit(unit.connection);
        } catch (RuntimeException | Error failure) {
            rollback(unit.connection, failure);
            throw failure;
        } finally {
            running.remove();
            close(unit.connection);
        }
        unit.runAfterCommit();
        return result;
    }

    public void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code action} once the running transaction commits, or at once when there is none. */
    public void afterCommit(Runnable action) {
        UnitOfWork unit = running.get();
        if (unit == null) {
            action.run();
        } else {
            unit.afterCommit.add(action);
        }
    }

    public boolean inTransaction() {
        return running.get() != null;
    }

    // ------------------------------------------------------------------ statements

    int update(String sql, Object... parameters) {
        return withConnection(sql, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                return statement.executeUpdate();
            }
        });
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
        return withConnection(sql, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    List<T> result = new ArrayList<>();
                    while (rows.next()) {
                        result.add(mapper.map(rows));
                    }
                    return result;
                }
            }
        });
    }

    // ------------------------------------------------------------------ identity map

    /** The instance already read or saved in this transaction for the given row, if any. */
    Tracked trackedByKey(String table, String id) {
        UnitOfWork unit = running.get();
        return unit == null ? null : unit.byKey.get(table + "/" + id);
    }

    /** The tracking entry of this very instance (identity, not equality), if any. */
    Tracked trackedByInstance(Object aggregate) {
        UnitOfWork unit = running.get();
        return unit == null ? null : unit.byInstance.get(aggregate);
    }

    void track(String table, String id, Object aggregate, long version) {
        UnitOfWork unit = running.get();
        if (unit == null) {
            return;
        }
        Tracked entry = new Tracked(table, id, aggregate, version);
        unit.byKey.put(table + "/" + id, entry);
        unit.byInstance.put(aggregate, entry);
    }

    // ------------------------------------------------------------------ plumbing

    private <T> T withConnection(String sql, SqlWork<T> work) {
        UnitOfWork unit = running.get();
        try {
            if (unit != null) {
                return work.run(unit.connection);
            }
            try (Connection connection = dataSource.getConnection()) {
                return work.run(connection);
            }
        } catch (SQLException e) {
            throw translate(sql, e);
        }
    }

    private Connection open() {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new PersistenceException("cannot open a database connection", e);
        }
    }

    private static void commit(Connection connection) {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw translate("COMMIT", e);
        }
    }

    private static void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    private static void close(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "could not close a database connection", e);
        }
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object value = parameters[i];
            switch (value) {
                case String text -> statement.setString(i + 1, text);
                case Long number -> statement.setLong(i + 1, number);
                case Integer number -> statement.setInt(i + 1, number);
                case Boolean flag -> statement.setBoolean(i + 1, flag);
                case null -> throw new PersistenceException("null statement parameter at position " + (i + 1));
                default -> statement.setObject(i + 1, value);
            }
        }
    }

    private static PersistenceException translate(String sql, SQLException e) {
        String message = "database failure (" + e.getSQLState() + ") on: " + sql + " -> " + e.getMessage();
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new DuplicateKeyException(message, e);
        }
        return new PersistenceException(message, e);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /** What the transaction knows about one aggregate it read or saved. */
    static final class Tracked {
        final String table;
        final String id;
        final Object aggregate;
        long version;

        Tracked(String table, String id, Object aggregate, long version) {
            this.table = table;
            this.id = id;
            this.aggregate = aggregate;
            this.version = version;
        }
    }

    private static final class UnitOfWork {
        final Connection connection;
        final Map<String, Tracked> byKey = new HashMap<>();
        final Map<Object, Tracked> byInstance = new IdentityHashMap<>();
        final List<Runnable> afterCommit = new ArrayList<>();

        UnitOfWork(Connection connection) {
            this.connection = connection;
        }

        void runAfterCommit() {
            for (Runnable action : afterCommit) {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "an after-commit action failed; the transaction is already committed", e);
                }
            }
        }
    }
}
