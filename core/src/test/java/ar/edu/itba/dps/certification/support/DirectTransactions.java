package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.function.Supplier;

/** Explicit adapter for unit tests with in-memory collaborators; it provides no database rollback. */
public enum DirectTransactions implements Transactions {
    INSTANCE;
    @Override public <T> T execute(Supplier<T> work) { return work.get(); }
    @Override public void execute(Runnable work) { work.run(); }
}
