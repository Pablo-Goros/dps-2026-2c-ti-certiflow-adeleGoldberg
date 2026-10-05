package ar.edu.itba.dps.certification.domain.certification.derivation;

public interface GlobalCertificatePolicy {

    GlobalCertificateDerivation deriveFrom(GlobalDerivationContext context);
}
