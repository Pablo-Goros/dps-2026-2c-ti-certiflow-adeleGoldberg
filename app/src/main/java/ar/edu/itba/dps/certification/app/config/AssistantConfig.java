package ar.edu.itba.dps.certification.app.config;

import ar.edu.itba.dps.certification.application.assistant.usecase.DecideSuggestion;
import ar.edu.itba.dps.certification.application.assistant.usecase.RequestAssistance;
import ar.edu.itba.dps.certification.application.audit.AuditRecorder;
import ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException;
import ar.edu.itba.dps.certification.domain.assistant.port.ConsultationRepository;
import ar.edu.itba.dps.certification.domain.assistant.port.InspectionAssistant;
import ar.edu.itba.dps.certification.domain.inspection.port.InspectionRepository;
import ar.edu.itba.dps.certification.domain.inspection.port.inbound.RecordObservedNonConformity;
import ar.edu.itba.dps.certification.domain.schema.port.SchemaCatalog;
import ar.edu.itba.dps.certification.domain.shared.port.ActorProvider;
import ar.edu.itba.dps.certification.domain.shared.port.Clock;
import ar.edu.itba.dps.certification.domain.shared.port.IdGenerator;
import ar.edu.itba.dps.certification.domain.shared.port.Transactions;
import ar.edu.itba.dps.certification.infrastructure.assistant.OpenAiCompatibleInspectionAssistant;
import ar.edu.itba.dps.certification.infrastructure.assistant.PromptLibrary;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The inspection assistant: its use cases and the adapter of the configured provider. Off unless
 * {@code certiflow.assistant.enabled=true}, because consultations need a {@link ConsultationRepository}
 * and a {@code BrowseConsultations}, which the persistence work provides with its own migration.
 *
 * <p>{@code certiflow.assistant.provider} picks the adapter: {@code openai-compatible} for any
 * service of that protocol (with base-url, model and, if the provider needs one, the key in
 * {@code CERTIFLOW_ASSISTANT_API_KEY}), or {@code none}, whose every consultation is a 503.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "certiflow.assistant.enabled", havingValue = "true")
class AssistantConfig {

    @Bean
    InspectionAssistant inspectionAssistant(
            @Value("${certiflow.assistant.provider:none}") String provider,
            @Value("${certiflow.assistant.base-url:}") String baseUrl,
            @Value("${certiflow.assistant.model:}") String model,
            @Value("${certiflow.assistant.api-key:}") String apiKey,
            @Value("${certiflow.assistant.prompt:suggest-findings/v1}") String prompt,
            @Value("${certiflow.assistant.timeout-seconds:60}") int timeoutSeconds) {
        return switch (provider.strip()) {
            case "none" -> request -> {
                throw new AssistanceUnavailableException(AssistanceUnavailableException.Reason.MISCONFIGURED,
                        "no assistant provider is configured (certiflow.assistant.provider)");
            };
            case OpenAiCompatibleInspectionAssistant.ADAPTER -> new OpenAiCompatibleInspectionAssistant(
                    URI.create(required(baseUrl, "certiflow.assistant.base-url")),
                    required(model, "certiflow.assistant.model"), Optional.of(apiKey),
                    PromptLibrary.load(prompt.strip(), OpenAiCompatibleInspectionAssistant.ADAPTER),
                    Duration.ofSeconds(timeoutSeconds));
            default -> throw new IllegalStateException("unknown assistant provider '" + provider
                    + "' in certiflow.assistant.provider: use none or " + OpenAiCompatibleInspectionAssistant.ADAPTER);
        };
    }

    @Bean
    RequestAssistance requestAssistance(InspectionRepository inspections, SchemaCatalog schemaCatalog, InspectionAssistant assistant, ConsultationRepository consultations, IdGenerator ids, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new RequestAssistance(inspections, schemaCatalog, assistant, consultations, ids, clock, audit, actors, transactions);
    }

    @Bean
    DecideSuggestion decideSuggestion(ConsultationRepository consultations, InspectionRepository inspections, RecordObservedNonConformity recordObservation, Clock clock, AuditRecorder audit, ActorProvider actors, Transactions transactions) {
        return new DecideSuggestion(consultations, inspections, recordObservation, clock, audit, actors, transactions);
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required by the " + OpenAiCompatibleInspectionAssistant.ADAPTER + " assistant");
        }
        return value.strip();
    }
}
