package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.application.certification.port.CertificateRepository;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.audit.AuditAction;
import ar.edu.itba.dps.certification.domain.audit.AuditDetail;
import ar.edu.itba.dps.certification.domain.audit.AuditedElementRef;
import ar.edu.itba.dps.certification.domain.certification.Certificate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ExpireDueCertificates {

    private final CertificateRepository certificates;
    private final Clock clock;
    private final AuditRecorder audit;

    public ExpireDueCertificates(CertificateRepository certificates, Clock clock, AuditRecorder audit) {
        this.certificates = certificates;
        this.clock = clock;
        this.audit = audit;
    }

    public List<Certificate> sweep() {
        Instant at = clock.now();
        List<Certificate> expired = new ArrayList<>();
        for (Certificate certificate : certificates.findDueForExpiry(at)) {
            String previousStatus = certificate.status().name();
            if (!certificate.expireIfDue(at)) {
                continue;
            }
            certificates.save(certificate);
            expired.add(certificate);
            audit.recordAutomatic(AuditedElementRef.certificate(certificate.id().value()),
                    AuditAction.CERTIFICATE_EXPIRED,
                    AuditDetail.stateChanged(previousStatus, "EXPIRED"));
        }
        return expired;
    }
}
