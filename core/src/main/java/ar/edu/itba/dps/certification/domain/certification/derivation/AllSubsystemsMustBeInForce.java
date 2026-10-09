package ar.edu.itba.dps.certification.domain.certification.derivation;

import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.CertificateMode;
import ar.edu.itba.dps.certification.domain.certification.ValidityPeriod;
import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AllSubsystemsMustBeInForce implements GlobalCertificatePolicy {

    @Override
    public GlobalCertificateDerivation deriveFrom(GlobalDerivationContext context) {
        Validate.required(context, "derivation context");
        Set<Subsystem> subsystems = context.subsystemsToCover();
        if (subsystems.isEmpty()) {
            return GlobalCertificateDerivation.notDerivable(List.of(
                    "asset " + context.assetId() + " declares no part, so it is certified as a "
                            + "whole by the certificate issued for inspection "
                            + context.inspectionId() + " rather than by a derived one"));
        }

        Map<Subsystem, Certificate> bySubsystem = context.partialsBySubsystem();

        List<String> reasons = subsystems.stream()
                .flatMap(subsystem -> whyItCannotBackAGlobal(bySubsystem, subsystem, context.at())
                        .stream())
                .toList();
        if (!reasons.isEmpty()) {
            return GlobalCertificateDerivation.notDerivable(reasons);
        }

        List<Certificate> backing = subsystems.stream().map(bySubsystem::get).toList();
        return GlobalCertificateDerivation.derived(new GlobalCertificate(
                context.assetId(),
                context.inspectionId(),
                context.schemaVersionId(),
                commonValidityOf(backing),
                new LinkedHashSet<>(subsystems),
                backing.stream().map(Certificate::id).toList(),
                backing.stream().anyMatch(c -> c.mode() == CertificateMode.CONDITIONAL)
                        ? CertificateMode.CONDITIONAL
                        : CertificateMode.REGULAR,
                backing.stream().map(c -> new GlobalCertificate.PartialProvenance(c.id(),
                        c.scope().coveredSubsystem().orElseThrow(), c.policy(), c.mode())).toList()));
    }

    private Optional<String> whyItCannotBackAGlobal(Map<Subsystem, Certificate> bySubsystem,
            Subsystem subsystem, Instant at) {
        Certificate partial = bySubsystem.get(subsystem);
        if (partial == null) {
            return Optional.of("subsystem " + subsystem + " has no certificate");
        }
        return partial.inForceAt(at)
                ? Optional.empty()
                : Optional.of("the certificate of subsystem " + subsystem + " is "
                        + whyNotInForce(partial, at));
    }

    private String whyNotInForce(Certificate certificate, Instant at) {
        return switch (certificate.status()) {
            case SUSPENDED -> "suspended";
            case EXPIRED -> "expired";
            case VALID -> "outside its validity period at " + at;
        };
    }

    private ValidityPeriod commonValidityOf(List<Certificate> partials) {
        Instant start = partials.stream().map(certificate -> certificate.validity().issuedAt())
                .max(Comparator.naturalOrder()).orElseThrow();
        Instant end = partials.stream().map(certificate -> certificate.validity().expiresAt())
                .min(Comparator.naturalOrder()).orElseThrow();
        return new ValidityPeriod(start, end);
    }
}
