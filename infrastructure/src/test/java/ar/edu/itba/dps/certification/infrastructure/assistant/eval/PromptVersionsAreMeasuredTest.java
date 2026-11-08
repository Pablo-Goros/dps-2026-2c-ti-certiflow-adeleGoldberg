package ar.edu.itba.dps.certification.infrastructure.assistant.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A prompt version is configuration, and a change to it is measured before it ships: every version
 * under {@code assistant/prompts/} needs its results from the harness committed to
 * {@code docs/assistant-eval/<prompt>-<version>.md}. A new version without them fails the build.
 */
class PromptVersionsAreMeasuredTest {

    private static final Path PROMPTS = Path.of("src", "main", "resources", "assistant", "prompts");
    private static final Path RESULTS = Path.of("..", "docs", "assistant-eval");

    @Test
    @DisplayName("every prompt version has its evaluation results committed")
    void everyPromptVersionWasMeasured() throws IOException {
        List<Path> versions = versions();

        assertThat(versions).isNotEmpty();
        assertThat(versions).allSatisfy(version -> {
            String name = version.getParent().getFileName() + "-" + version.getFileName();
            Path results = RESULTS.resolve(name + ".md");
            assertThat(results).as("results of " + version.getParent().getFileName() + "/" + version.getFileName()
                    + ": run the harness (-Passistant-eval) and commit them to " + results).exists();
            assertThat(Files.readString(results))
                    .contains(version.getParent().getFileName() + "/" + version.getFileName());
        });
    }

    private static List<Path> versions() throws IOException {
        try (Stream<Path> prompts = Files.list(PROMPTS)) {
            return prompts.filter(Files::isDirectory).flatMap(prompt -> {
                try (Stream<Path> versions = Files.list(prompt)) {
                    return versions.filter(Files::isDirectory)
                            .filter(version -> version.getFileName().toString().matches("v[0-9]+"))
                            .toList().stream();
                } catch (IOException e) {
                    throw new IllegalStateException("cannot list " + prompt, e);
                }
            }).sorted().toList();
        }
    }
}
