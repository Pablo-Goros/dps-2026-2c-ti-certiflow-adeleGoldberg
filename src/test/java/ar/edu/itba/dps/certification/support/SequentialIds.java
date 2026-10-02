package ar.edu.itba.dps.certification.support;

import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

public final class SequentialIds implements IdGenerator {

    private final String prefix;
    private final AtomicLong counter = new AtomicLong();

    public SequentialIds() {
        this("id");
    }

    public SequentialIds(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String newIdentifier() {
        return prefix + "-" + counter.incrementAndGet();
    }
}
