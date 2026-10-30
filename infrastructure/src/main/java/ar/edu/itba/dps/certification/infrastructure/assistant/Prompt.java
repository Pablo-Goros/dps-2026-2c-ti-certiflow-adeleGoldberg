package ar.edu.itba.dps.certification.infrastructure.assistant;

/**
 * One version of a prompt as the adapters send it: the instructions every provider receives, the
 * JSON schema of the answer and the parameters of one adapter, as read from {@link PromptLibrary}.
 */
public record Prompt(String version, String instructions, String outputSchema, String parameters) {

    public Prompt {
        version = required(version, "prompt version");
        instructions = required(instructions, "prompt instructions");
        outputSchema = required(outputSchema, "prompt output schema");
        parameters = required(parameters, "prompt parameters");
    }

    private static String required(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " is required and cannot be blank");
        }
        return text;
    }
}
