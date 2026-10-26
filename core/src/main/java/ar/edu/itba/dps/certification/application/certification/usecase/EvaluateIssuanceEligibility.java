package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.certification.CertificateFactory;
import ar.edu.itba.dps.certification.domain.catalogue.Subsystem;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;

import java.util.List;

/** Answers "could this inspection back a certificate now?" with the same rules issuance applies. */
public final class EvaluateIssuanceEligibility implements ar.edu.itba.dps.certification.domain.certification.port.inbound.EvaluateIssuanceEligibility {
    private final Transactions transactions;

    private final CertificateFactory factory;

    public EvaluateIssuanceEligibility(CertificateFactory factory, Transactions transactions) {
        this.transactions = transactions;
        this.factory = factory;
    }

    public CertificationAssessment assess(InspectionId inspectionId) {
        return transactions.execute(() -> {
            return factory.assess(inspectionId);
        });
    }
    public CertificationAssessment assess(InspectionId inspectionId, Subsystem subsystem) {
        return transactions.execute(() -> {
            return factory.assess(inspectionId, subsystem);
        });
    }

    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId) {
        return transactions.execute(() -> {
            return factory.blockersFor(inspectionId);
        });
    }

    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId, Subsystem subsystem) {
        return transactions.execute(() -> {
            return factory.blockersFor(inspectionId, subsystem);
        });
    }
}
