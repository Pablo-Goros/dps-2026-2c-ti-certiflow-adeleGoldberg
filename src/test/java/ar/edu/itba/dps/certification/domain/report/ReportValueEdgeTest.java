package ar.edu.itba.dps.certification.domain.report;

import ar.edu.itba.dps.certification.domain.certification.CertificateId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportValueEdgeTest {

    @Test
    @DisplayName("reported values expose original and rectified states")
    void reportedValuesExposeOriginalAndRectifiedStates() {
        ReportedValue<String> original = ReportedValue.original("old");
        ReportedValue<String> rectified = ReportedValue.rectified("old", "new",
                RectificationId.of("rect-1"), "typo");

        assertThat(original.current()).isEqualTo("old");
        assertThat(original.rectified()).isFalse();
        assertThat(rectified.current()).isEqualTo("new");
        assertThat(rectified.rectified()).isTrue();
    }

    @Test
    @DisplayName("issuance reports enforce consistent certified and blocked states")
    void issuanceReportsEnforceConsistentStates() {
        InspectionId inspectionId = InspectionId.of("inspection-1");
        CertificateId certificateId = CertificateId.of("certificate-1");

        assertThat(IssuanceAttemptReport.certified(inspectionId, certificateId).certificateId())
                .contains(certificateId);
        assertThat(IssuanceAttemptReport.blocked(inspectionId, List.of("blocked")).blockingReasons())
                .containsExactly("blocked");
        assertThatThrownBy(() -> new IssuanceAttemptReport(inspectionId, true,
                Optional.empty(), List.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("either certifies");
        assertThatThrownBy(() -> new IssuanceAttemptReport(inspectionId, true,
                Optional.of(certificateId), List.of("blocked")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("certified issuance has no blockers");
    }
}
