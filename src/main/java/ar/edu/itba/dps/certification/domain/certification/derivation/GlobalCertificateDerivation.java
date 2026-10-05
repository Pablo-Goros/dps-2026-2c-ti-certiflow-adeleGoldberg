package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;
import java.util.Optional;

public sealed interface GlobalCertificateDerivation {

    Optional<GlobalCertificate> derivedCertificate();

    String describe();

    record Derived(GlobalCertificate certificate) implements GlobalCertificateDerivation {

        public Derived {
            Validate.required(certificate, "global certificate");
        }

        @Override
        public Optional<GlobalCertificate> derivedCertificate() {
            return Optional.of(certificate);
        }

        @Override
        public String describe() {
            return certificate.describe();
        }
    }

    record NotDerivable(List<String> reasons) implements GlobalCertificateDerivation {

        public NotDerivable {
            reasons = List.copyOf(Validate.requiredNonEmpty(reasons, "reasons"));
        }

        @Override
        public Optional<GlobalCertificate> derivedCertificate() {
            return Optional.empty();
        }

        @Override
        public String describe() {
            return String.join("; ", reasons);
        }
    }

    static GlobalCertificateDerivation derived(GlobalCertificate certificate) {
        return new Derived(certificate);
    }

    static GlobalCertificateDerivation notDerivable(List<String> reasons) {
        return new NotDerivable(reasons);
    }
}
