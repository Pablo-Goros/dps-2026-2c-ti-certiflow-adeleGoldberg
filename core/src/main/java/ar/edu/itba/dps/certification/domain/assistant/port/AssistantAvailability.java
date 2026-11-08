package ar.edu.itba.dps.certification.domain.assistant.port;

/**
 * Reference data for the interfaces: whether this deployment has the inspection assistant on, and
 * which adapter answers. It names the adapter only, never its address, model or credentials.
 */
public interface AssistantAvailability {

    boolean enabled();

    String adapter();
}
