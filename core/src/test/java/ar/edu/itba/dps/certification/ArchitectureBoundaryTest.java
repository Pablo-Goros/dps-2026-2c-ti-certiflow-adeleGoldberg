package ar.edu.itba.dps.certification;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class ArchitectureBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final String ROOT_PACKAGE = "ar.edu.itba.dps.certification";

    @Test
    void domainDoesNotDependOnApplicationOrAdapters() throws IOException {
        assertNoSourceContains(MAIN_SOURCES.resolve(Path.of("ar", "edu", "itba", "dps", "certification", "domain")),
                List.of(
                        "import " + ROOT_PACKAGE + ".application.",
                        "import " + ROOT_PACKAGE + ".adapter."
                ));
    }

    @Test
    void applicationDoesNotDependOnAdapters() throws IOException {
        assertNoSourceContains(MAIN_SOURCES.resolve(Path.of("ar", "edu", "itba", "dps", "certification", "application")),
                List.of("import " + ROOT_PACKAGE + ".adapter."));
    }

    @Test
    void theCoreKnowsNeitherFrameworksNorDatabasesNorOtherModules() throws IOException {
        assertNoSourceContains(MAIN_SOURCES, List.of(
                "import org.springframework.",
                "import jakarta.",
                "import java.sql.",
                "import javax.sql.",
                "import tools.jackson.",
                "import com.fasterxml.",
                "import " + ROOT_PACKAGE + ".infrastructure.",
                "import " + ROOT_PACKAGE + ".app."));
    }

    @Test
    void theCatalogueDoesNotDependOnTheSchema() throws IOException {
        assertNoSourceContains(
                MAIN_SOURCES.resolve(Path.of("ar", "edu", "itba", "dps", "certification", "domain", "catalogue")),
                List.of("import " + ROOT_PACKAGE + ".domain.schema."));
    }

    @Test
    void domainDoesNotDeclarePorts() throws IOException {
        Path domain = MAIN_SOURCES.resolve(Path.of("ar", "edu", "itba", "dps", "certification", "domain"));
        try (var files = Files.walk(domain)) {
            var violations = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(ArchitectureBoundaryTest::declaresDomainPortPackage)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static void assertNoSourceContains(Path root, List<String> forbiddenSnippets) throws IOException {
        try (var files = Files.walk(root)) {
            var violations = files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .flatMap(file -> violationsIn(file, forbiddenSnippets).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static List<String> violationsIn(Path file, List<String> forbiddenSnippets) {
        try {
            String source = Files.readString(file);
            return forbiddenSnippets.stream()
                    .filter(source::contains)
                    .map(snippet -> file + " contains '" + snippet + "'")
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }

    private static boolean declaresDomainPortPackage(Path file) {
        try {
            String source = Files.readString(file);
            return source.contains("package " + ROOT_PACKAGE + ".domain.")
                    && source.contains(".port;");
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
