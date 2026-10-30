package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

import java.util.List;

/** What an adapter obtained from its provider, before the domain validates it. */
public record AssistantReply(AssistantVersion version, List<SuggestedFinding> findings) {

    public AssistantReply {
        Validate.required(version, "assistant version");
        findings = List.copyOf(Validate.required(findings, "suggested findings"));
    }
}
