package ar.edu.itba.dps.certification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The guardrails of the assistant are structural: its code cannot reach the operations it must never run. */
final class AssistantBoundaryTest {

    private static final String ROOT_PACKAGE = "ar.edu.itba.dps.certification";
    private static final Path ROOT = Path.of("src", "main", "java", "ar", "edu", "itba", "dps", "certification");

    @Test
    void theAssistantCannotCloseInspectionsIssueCertificatesNorRegisterFindings() throws IOException {
        List<String> forbidden = List.of(
                "import " + ROOT_PACKAGE + ".domain.certification.",
                "import " + ROOT_PACKAGE + ".application.certification.",
                "import " + ROOT_PACKAGE + ".domain.finding.",
                "import " + ROOT_PACKAGE + ".application.finding.",
                "FindingRegistry", "CloseInspection", "RectifyClosedInspection",
                // It reads inspections; only RecordObservedNonConformity may change one.
                "inspections.save(", ".close(", ".rectify(");
        for (Path area : List.of(ROOT.resolve(Path.of("domain", "assistant")),
                ROOT.resolve(Path.of("application", "assistant")), ROOT.resolve(Path.of("adapter", "assistant")))) {
            if (!Files.isDirectory(area)) {
                continue;
            }
            try (var files = Files.walk(area)) {
                var violations = files.filter(file -> file.toString().endsWith(".java"))
                        .flatMap(file -> forbidden.stream().filter(source(file)::contains)
                                .map(snippet -> file + " contains '" + snippet + "'"))
                        .toList();
                assertThat(violations).isEmpty();
            }
        }
    }

    @Test
    void nothingElseInTheCoreKnowsTheAssistant() throws IOException {
        try (var files = Files.walk(ROOT)) {
            var violations = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !ROOT.relativize(file).toString().contains("assistant"))
                    .filter(file -> source(file).contains("import " + ROOT_PACKAGE + ".domain.assistant.")
                            || source(file).contains("import " + ROOT_PACKAGE + ".application.assistant.")
                            || source(file).contains("import " + ROOT_PACKAGE + ".adapter.assistant."))
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static String source(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
