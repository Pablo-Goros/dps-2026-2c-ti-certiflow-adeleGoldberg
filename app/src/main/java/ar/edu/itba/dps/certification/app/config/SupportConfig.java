package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.application.certification.CertificationReactions;
import ar.edu.itba.dps.certification.application.shared.port.Clock;
import ar.edu.itba.dps.certification.application.shared.port.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.UUID;

/** Technical ports of the core: time, identifiers, current actor and event delivery. */
@Configuration(proxyBeanMethods = false)
class SupportConfig {

    @Bean
    Clock clock(@Value("${certiflow.zone:America/Argentina/Buenos_Aires}") String zone) {
        return new SystemClock(ZoneId.of(zone));
    }

    @Bean
    IdGenerator ids() {
        return () -> UUID.randomUUID().toString();
    }

    @Bean
    RequestActor actors() {
        return new RequestActor();
    }

    @Bean
    SynchronousEventPublisher events(CertificationReactions reactions) {
        var publisher = new SynchronousEventPublisher();
        publisher.register(reactions);
        return publisher;
    }
}
