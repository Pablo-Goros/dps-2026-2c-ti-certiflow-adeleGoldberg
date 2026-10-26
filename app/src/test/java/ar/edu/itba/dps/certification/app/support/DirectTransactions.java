package ar.edu.itba.dps.certification.app.support;

import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.function.Supplier;

/** Explicit unit-test adapter; database integration tests use JdbcTransactions. */
public enum DirectTransactions implements Transactions {
    INSTANCE;
    @Override public <T> T execute(Supplier<T> work) { return work.get(); }
    @Override public void execute(Runnable work) { work.run(); }
}
