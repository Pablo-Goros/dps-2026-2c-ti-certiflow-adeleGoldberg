package ar.edu.itba.dps.certification.domain.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditedElementRefEdgeTest {

    @Test
    @DisplayName("audited element factories cover every audited aggregate")
    void auditedElementFactoriesCoverEveryAuditedAggregate() {
        assertThat(AuditedElementRef.asset("a").toString()).isEqualTo("ASSET:a");
        assertThat(AuditedElementRef.party("p").toString()).isEqualTo("PARTY:p");
        assertThat(AuditedElementRef.schema("s").toString()).isEqualTo("SCHEMA:s");
        assertThat(AuditedElementRef.inspection("i").toString()).isEqualTo("INSPECTION:i");
        assertThat(AuditedElementRef.finding("f").toString()).isEqualTo("FINDING:f");
        assertThat(AuditedElementRef.correctiveAction("c").toString()).isEqualTo("CORRECTIVE_ACTION:c");
        assertThat(AuditedElementRef.certificate("cert").toString()).isEqualTo("CERTIFICATE:cert");
    }
}
