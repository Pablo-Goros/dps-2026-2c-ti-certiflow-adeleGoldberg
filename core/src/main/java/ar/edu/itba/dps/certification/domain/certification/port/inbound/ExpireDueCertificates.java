package ar.edu.itba.dps.certification.domain.certification.port.inbound;

import ar.edu.itba.dps.certification.domain.certification.Certificate;

import java.util.List;

/** Input contract implemented by the application use case. */
public interface ExpireDueCertificates {
    List<Certificate> sweep();
}
