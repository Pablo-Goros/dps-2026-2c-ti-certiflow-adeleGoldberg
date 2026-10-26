package ar.edu.itba.dps.certification.domain.shared.port;

import java.util.function.Supplier;

/**
 * Unit of work boundary. Everything a use case writes inside {@code execute} is stored together or
 * not at all; a failure rolls everything back and is rethrown untouched. Nested calls join the
 * running unit. Application use cases open their own unit, regardless of the driving adapter.
 */
public interface Transactions {

    <T> T execute(Supplier<T> work);

    void execute(Runnable work);
}
