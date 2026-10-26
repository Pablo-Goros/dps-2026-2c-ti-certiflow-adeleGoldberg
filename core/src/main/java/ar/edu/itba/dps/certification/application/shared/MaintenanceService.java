package ar.edu.itba.dps.certification.application.shared;

import ar.edu.itba.dps.certification.domain.certification.port.inbound.ExpireDueCertificates;
import ar.edu.itba.dps.certification.domain.finding.port.inbound.ExpireOverdueCorrectiveActions;
import ar.edu.itba.dps.certification.domain.shared.port.inbound.PublishPendingDomainEvents;
import ar.edu.itba.dps.certification.domain.shared.port.inbound.RunMaintenance;
import ar.edu.itba.dps.certification.domain.shared.port.OutboxOperations;

/** Each sweep owns its unit of work. Deliveries run between committed units, never inside a batch transaction. */
public final class MaintenanceService implements RunMaintenance {
    private final ExpireDueCertificates certificates;
    private final ExpireOverdueCorrectiveActions actions;
    private final PublishPendingDomainEvents republish;
    private final OutboxOperations outbox;

    public MaintenanceService(ExpireDueCertificates certificates, ExpireOverdueCorrectiveActions actions,
            PublishPendingDomainEvents republish, OutboxOperations outbox) {
        this.certificates = certificates;
        this.actions = actions;
        this.republish = republish;
        this.outbox = outbox;
    }

    @Override
    public Result runAll() {
        int expiredActions = actions.sweep().size();
        int delivered = outbox.dispatchPending();
        int expiredCertificates = certificates.sweep().size();
        republish.publish();
        delivered += outbox.dispatchPending();
        return new Result(expiredCertificates, expiredActions, delivered);
    }
}
