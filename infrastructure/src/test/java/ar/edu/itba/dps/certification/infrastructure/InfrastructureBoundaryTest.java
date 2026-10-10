package ar.edu.itba.dps.certification.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The driven adapters depend on the core, never on the application that assembles them and never on
 * the web framework (source scan, like the core's own boundary test).
 */
class InfrastructureBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final String ROOT_PACKAGE = "ar.edu.itba.dps.certification";

    @Test
    void infrastructureDoesNotKnowTheApplicationOrTheWebFramework() throws IOException {
        try (var files = Files.walk(MAIN_SOURCES)) {
            var violations = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> violationsIn(file).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static List<String> violationsIn(Path file) {
        List<String> forbidden = List.of(
                "import " + ROOT_PACKAGE + ".app.",
                "import org.springframework.",
                "import jakarta.servlet.");
        try {
            String source = Files.readString(file);
            return forbidden.stream()
                    .filter(source::contains)
                    .map(snippet -> file + " contains '" + snippet + "'")
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
