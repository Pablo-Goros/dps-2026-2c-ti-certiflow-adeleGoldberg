package ar.edu.itba.dps.certification.domain.assistant.port;

import ar.edu.itba.dps.certification.domain.assistant.AssistanceRequest;
import ar.edu.itba.dps.certification.domain.assistant.AssistantReply;

/** The model behind the assistant. Adapters exist per provider; the core depends on none of them. */
public interface InspectionAssistant {

    /** @throws ar.edu.itba.dps.certification.domain.assistant.AssistanceUnavailableException if no complete, readable reply could be obtained */
    AssistantReply suggest(AssistanceRequest request);
}
