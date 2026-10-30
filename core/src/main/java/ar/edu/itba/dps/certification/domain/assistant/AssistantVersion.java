package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.shared.Validate;

/** The adapter, the model that actually answered and the prompt version: what produced a suggestion. */
public record AssistantVersion(String adapter, String model, String prompt) {

    public AssistantVersion {
        adapter = Validate.requiredText(adapter, "assistant adapter");
        model = Validate.requiredText(model, "assistant model");
        prompt = Validate.requiredText(prompt, "assistant prompt");
    }

    public String describe() {
        return adapter + " " + model + " " + prompt;
    }
}
