package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;

import java.time.Instant;

public final class CertificateFixtures {
    public static void suspend(Certificate certificate, Instant at) {
        certificate.suspend(new SuspensionCause.OverdueAction(CorrectiveActionId.of("test-action")), at);
    }
}
