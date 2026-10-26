import ar.edu.itba.dps.certification.app.CertiflowApplication;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.adapter.certification.RegisteredCertificationPolicies;
import ar.edu.itba.dps.certification.domain.catalogue.JurisdictionId;
import ar.edu.itba.dps.certification.domain.certification.policy.*;
import ar.edu.itba.dps.certification.domain.schema.Severity;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Audit-only composition harness: baseline JAR classes, actual Spring HTTP/JDBC, controlled clock. */
public final class AuditControlledApp {
    static final class FileClock implements Clock {
        public Instant now() {
            try { return Instant.parse(Files.readString(Path.of(System.getProperty("audit.clock"))).trim()); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }
        public LocalDate today() { return now().atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate(); }
    }
    public static void main(String[] args) throws Exception {
        var app = new SpringApplication(CertiflowApplication.class);
        app.addInitializers(context -> {
            var definition = new RootBeanDefinition(FileClock.class, FileClock::new);
            definition.setPrimary(true);
            ((BeanDefinitionRegistry) context.getBeanFactory()).registerBeanDefinition("auditClock", definition);
        });
        var context = app.run(args);
        if (Integer.getInteger("audit.policyRevision", 1) == 2) {
            context.getBean(RegisteredCertificationPolicies.class).register(JurisdictionId.of("AR-BA"),
                new ConfiguredJurisdictionCertificationPolicy(new CertificationPolicySnapshot(
                    new CertificationPolicyRef(JurisdictionId.of("AR-BA"), "Buenos Aires", 2),
                    Set.of(Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL), false,
                    Period.ofMonths(3), Period.ofMonths(3), Set.of())));
        }
        Files.writeString(Path.of(System.getProperty("audit.ready")), "ready");
        // Closing Spring explicitly stops HTTP and closes Hikari/H2; ordinary exit follows.
        new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
        context.close();
    }
}
