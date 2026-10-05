package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.Optional;

public sealed interface CertificateScope {

    String describe();

    Optional<Subsystem> coveredSubsystem();

    record Global() implements CertificateScope {

        @Override
        public String describe() {
            return "the asset as a whole";
        }

        @Override
        public Optional<Subsystem> coveredSubsystem() {
            return Optional.empty();
        }
    }

    record Partial(Subsystem subsystem) implements CertificateScope {

        public Partial {
            Validate.required(subsystem, "subsystem");
        }

        @Override
        public String describe() {
            return "subsystem " + subsystem;
        }

        @Override
        public Optional<Subsystem> coveredSubsystem() {
            return Optional.of(subsystem);
        }
    }

    static CertificateScope global() {
        return new Global();
    }

    static CertificateScope of(Subsystem subsystem) {
        return new Partial(subsystem);
    }
}
