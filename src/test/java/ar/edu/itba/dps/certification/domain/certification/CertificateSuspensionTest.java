package ar.edu.itba.dps.certification.domain.certification;

import ar.edu.itba.dps.certification.domain.catalogue.AssetId;
import ar.edu.itba.dps.certification.domain.certification.suspension.SuspensionCause;
import ar.edu.itba.dps.certification.domain.finding.action.CorrectiveActionId;
import ar.edu.itba.dps.certification.domain.inspection.InspectionId;
import ar.edu.itba.dps.certification.domain.inspection.rectification.RectificationId;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.SchemaId;
import ar.edu.itba.dps.certification.domain.schema.SchemaVersionId;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;

class CertificateSuspensionTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2027-03-01T10:00:00Z");
    private static final Instant DURING = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant AFTER_EXPIRY = Instant.parse("2027-06-01T10:00:00Z");

    private static final SuspensionCause FIRST_CAUSE =
            new SuspensionCause.OverdueAction(CorrectiveActionId.of("action-1"));
    private static final SuspensionCause SECOND_CAUSE =
            new SuspensionCause.RectifiedRejection(CriterionId.of("TEMP"),
                    RectificationId.of("rect-1"));

    private Certificate certificate;

    @BeforeEach
    void setUp() {
        certificate = new Certificate(
                CertificateId.of("cert-1"),
                AssetId.of("asset-1"),
                InspectionId.of("inspection-1"),
                new SchemaVersionId(SchemaId.of("schema-1"), 1),
                new ValidityPeriod(ISSUED_AT, EXPIRES_AT),
                null);
    }

    @Test
    @DisplayName("reactivation waits for the last cause, not the first")
    void reactivationRequiresEveryCauseToBeResolved() {
        certificate.suspend(FIRST_CAUSE, DURING);
        certificate.suspend(SECOND_CAUSE, DURING);

        boolean afterFirst = certificate.resolveCause(FIRST_CAUSE, "verified and closed", DURING);

        assertThat(afterFirst).isFalse();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).containsExactly(SECOND_CAUSE);

        boolean afterSecond = certificate.resolveCause(SECOND_CAUSE, "verified and closed", DURING);

        assertThat(afterSecond).isTrue();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    @DisplayName("reactivation keeps the original expiry date and does not compensate the suspended time")
    void reactivationKeepsTheOriginalExpiry() {
        certificate.suspend(FIRST_CAUSE, DURING);
        certificate.resolveCause(FIRST_CAUSE, "verified and closed", DURING.plusSeconds(86_400));

        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.validity().expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("an expired certificate is not reactivated even when every cause is resolved")
    void anExpiredCertificateIsNeverReactivated() {
        certificate.suspend(FIRST_CAUSE, DURING);

        boolean reactivated = certificate.resolveCause(FIRST_CAUSE, "verified and closed", AFTER_EXPIRY);

        assertThat(reactivated).isFalse();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).isEmpty();
    }

    @Test
    @DisplayName("the same cause raised twice is a no-op")
    void raisingTheSameCauseTwiceChangesNothing() {
        assertThat(certificate.suspend(FIRST_CAUSE, DURING)).isTrue();
        assertThat(certificate.suspend(FIRST_CAUSE, DURING.plusSeconds(60))).isFalse();
        assertThat(certificate.suspensions()).hasSize(1);
    }

    @Test
    @DisplayName("an expired certificate cannot be suspended")
    void anExpiredCertificateIsNotSuspended() {
        certificate.expireIfDue(AFTER_EXPIRY);

        assertThat(certificate.suspend(FIRST_CAUSE, AFTER_EXPIRY)).isFalse();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.EXPIRED);
    }

    @Test
    @DisplayName("a certificate past its expiry instant cannot be suspended before the sweep marks it")
    void aCertificatePastItsExpiryIsNotSuspendedWhileStillUnmarked() {
        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);

        assertThat(certificate.suspend(FIRST_CAUSE, AFTER_EXPIRY)).isFalse();

        assertThat(certificate.status()).isEqualTo(CertificateStatus.VALID);
        assertThat(certificate.suspensions()).isEmpty();
        assertThat(certificate.expireIfDue(AFTER_EXPIRY)).isTrue();
    }

    @Test
    @DisplayName("a certificate does not cover a moment before it was issued")
    void aCertificateDoesNotCoverAMomentBeforeItsIssuance() {
        assertThat(certificate.coversMoment(ISSUED_AT.minusSeconds(1))).isFalse();
        assertThat(certificate.coversMoment(ISSUED_AT)).isTrue();
        assertThat(certificate.coversMoment(DURING)).isTrue();
        assertThat(certificate.coversMoment(EXPIRES_AT)).isFalse();
    }

    @Test
    @DisplayName("a certificate cannot be recorded as its own predecessor")
    void aCertificateCannotSucceedItself() {
        assertThatThrownBy(() -> new Certificate(
                CertificateId.of("cert-1"),
                AssetId.of("asset-1"),
                InspectionId.of("inspection-1"),
                new SchemaVersionId(SchemaId.of("schema-1"), 1),
                new ValidityPeriod(ISSUED_AT, EXPIRES_AT),
                CertificateId.of("cert-1")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("cannot succeed itself");
    }

    @Test
    @DisplayName("the suspension history survives reactivation")
    void theSuspensionHistoryIsKept() {
        certificate.suspend(FIRST_CAUSE, DURING);
        certificate.resolveCause(FIRST_CAUSE, "verified and closed", DURING);

        assertThat(certificate.suspensions()).singleElement().satisfies(record -> {
            assertThat(record.cause()).isEqualTo(FIRST_CAUSE);
            assertThat(record.raisedAt()).isEqualTo(DURING);
            assertThat(record.resolution()).contains("verified and closed");
        });
    }

    @Test
    @DisplayName("resolving a returned suspension record does not mutate the certificate")
    void resolvingAReturnedSuspensionRecordDoesNotMutateTheCertificate() {
        certificate.suspend(FIRST_CAUSE, DURING);

        var leakedRecord = certificate.suspensions().getFirst();
        var resolvedCopy = leakedRecord.resolved("resolved outside the aggregate", DURING);

        assertThat(resolvedCopy.unresolved()).isFalse();
        assertThat(certificate.status()).isEqualTo(CertificateStatus.SUSPENDED);
        assertThat(certificate.unresolvedCauses()).containsExactly(FIRST_CAUSE);
        assertThat(certificate.suspensions()).singleElement().satisfies(record -> {
            assertThat(record.unresolved()).isTrue();
            assertThat(record.resolution()).isEmpty();
        });
    }
}
