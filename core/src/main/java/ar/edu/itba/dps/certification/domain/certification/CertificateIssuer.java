package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.certification.issuance.CertificationAssessment;
import ar.edu.itba.dps.certification.domain.inspection.Inspection;

/** Domain entry point: construction requires evaluated facts, never arbitrary validity or provenance. */
public final class CertificateIssuer {
    public Certificate issue(CertificateId id, Inspection inspection,
            CertificationAssessment assessment, Certificate previous) {
        return new Certificate(id, inspection, assessment, previous);
    }
}
