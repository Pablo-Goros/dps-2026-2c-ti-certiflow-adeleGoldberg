package ar.edu.itba.dps.certification.application.certification.usecase;

import ar.edu.itba.dps.certification.application.certification.CertificationContextAssembler;
import ar.edu.itba.dps.certification.application.inspection.port.InspectionQuery;
import ar.edu.itba.dps.certification.domain.certification.issuance.CertificateIssuancePolicy;
import ar.edu.itba.dps.certification.domain.certification.issuance.IssuanceBlocker;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;

import java.util.List;

public final class EvaluateIssuanceEligibility {

    private final InspectionQuery inspections;
    private final CertificationContextAssembler assembler;
    private final CertificateIssuancePolicy policy;

    public EvaluateIssuanceEligibility(InspectionQuery inspections,
            CertificationContextAssembler assembler, CertificateIssuancePolicy policy) {
        this.inspections = inspections;
        this.assembler = assembler;
        this.policy = policy;
    }

    public List<IssuanceBlocker> blockersFor(InspectionId inspectionId) {
        return policy.blockersFor(
                assembler.contextFor(inspections.summaryOf(inspectionId)));
    }
}
