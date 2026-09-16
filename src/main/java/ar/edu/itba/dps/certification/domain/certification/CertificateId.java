package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.shared.Validate;

public record CertificateId(String value) {

    public CertificateId {
        value = Validate.requiredText(value, "certificate id");
    }

    public static CertificateId of(String value) {
        return new CertificateId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
