package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;
import ar.edu.itba.dps.certification.domain.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ExpireDueCertificates implements ar.edu.itba.dps.certification.domain.certification.port.inbound.ExpireDueCertificates {
    private final Transactions transactions;

    private final CertificateRepository certificates;
    private final Clock clock;
    private final AuditRecorder audit;

    public ExpireDueCertificates(CertificateRepository certificates, Clock clock, AuditRecorder audit, Transactions transactions) {
        this.transactions = transactions;
        this.certificates = certificates;
        this.clock = clock;
        this.audit = audit;
    }

    public List<Certificate> sweep() {
        return transactions.execute(() -> {
            Instant at = clock.now();
            List<Certificate> expired = new ArrayList<>();
            for (Certificate certificate : certificates.findDueForExpiry(at)) {
                var previousStatus = certificate.status();
                if (!certificate.expireIfDue(at)) {
                    continue;
                }
                certificates.save(certificate);
                expired.add(certificate);
                audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                        AuditAction.CERTIFICATE_EXPIRED,
                        AuditDetail.certificateStateChanged(previousStatus, certificate));
            }
            return expired;
        });
    }
}
