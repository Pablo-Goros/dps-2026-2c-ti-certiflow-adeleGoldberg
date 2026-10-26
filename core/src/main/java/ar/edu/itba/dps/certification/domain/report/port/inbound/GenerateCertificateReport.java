package ar.edu.itba.dps.certification.domain.report.port.inbound;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceDecision;
import ar.edu.itba.dps.certification.domain.report.CertificateReport;
import ar.edu.itba.dps.certification.domain.report.IssuanceAttemptReport;

/** Input contract implemented by the application use case. */
public interface GenerateCertificateReport {
    CertificateReport generate(CertificateId certificateId);

    IssuanceAttemptReport reportBlockedAttempt(
            IssuanceDecision.Blocked blocked);
}
