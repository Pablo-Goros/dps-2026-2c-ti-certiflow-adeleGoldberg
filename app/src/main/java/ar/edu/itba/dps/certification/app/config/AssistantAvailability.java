package ar.edu.itba.dps.certification.app.config;

/** What the configuration says about the inspection assistant: on or off, and the adapter it uses. */
public record AssistantAvailability(boolean enabled, String adapter)
        implements ar.edu.itba.dps.certification.domain.assistant.port.AssistantAvailability {
}
