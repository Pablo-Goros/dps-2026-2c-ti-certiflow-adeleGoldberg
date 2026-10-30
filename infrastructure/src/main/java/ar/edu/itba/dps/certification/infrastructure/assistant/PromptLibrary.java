package ar.edu.itba.dps.certification.infrastructure.assistant;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The versioned prompts on the classpath, one folder per version under {@code assistant/prompts/}:
 * {@code instructions.md} with the text every provider receives, {@code output.schema.json} with the
 * shape of the answer, and one {@code <adapter>.json} with the parameters of each adapter.
 *
 * <p>A version is not edited once it has been measured. A change is a new version, measured again
 * with the evaluation harness.
 */
public final class PromptLibrary {

    private static final String ROOT = "assistant/prompts/";
    private static final Pattern VERSION = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*/v[0-9]+");
    private static final Pattern ADAPTER = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final ObjectMapper JSON = new ObjectMapper();

    private PromptLibrary() {
    }

    /**
     * @param version a prompt name and version, such as {@code suggest-findings/v1}
     * @param adapter the name of the adapter whose parameters to read from {@code <adapter>.json}
     * @throws IllegalArgumentException when the version or the adapter's parameters do not exist, or a
     *         JSON file of the version does not hold a JSON object
     */
    public static Prompt load(String version, String adapter) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("'" + version + "' is not a prompt version such as suggest-findings/v1");
        }
        if (adapter == null || !ADAPTER.matcher(adapter).matches()) {
            throw new IllegalArgumentException("'" + adapter + "' is not an adapter name: lowercase letters, digits and hyphens");
        }
        String folder = ROOT + version + "/";
        return new Prompt(version, read(folder + "instructions.md"), jsonObject(folder + "output.schema.json"),
                jsonObject(folder + adapter + ".json"));
    }

    private static String jsonObject(String resource) {
        String text = read(resource);
        try {
            if (!JSON.readTree(text).isObject()) {
                throw new IllegalArgumentException(resource + " must hold a JSON object");
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException(resource + " is not valid JSON: " + e.getOriginalMessage(), e);
        }
        return text;
    }

    private static String read(String resource) {
        try (InputStream in = PromptLibrary.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("prompt file " + resource + " does not exist");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read prompt file " + resource, e);
        }
    }
}
