package ar.edu.itba.dps.certification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

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
    void portsBelongToTheDomainAndEveryApplicationUseCaseImplementsItsInputPort() throws Exception {
        Path application = MAIN_SOURCES.resolve(Path.of("ar", "edu", "itba", "dps", "certification", "application"));
        try (var files = Files.walk(application)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(file.getParent().getFileName().toString()).isNotEqualTo("port");
                if (!file.getParent().getFileName().toString().equals("usecase")) continue;
                String name = file.getFileName().toString().replace(".java", "");
                String feature = file.getParent().getParent().getFileName().toString();
                Class<?> input = Class.forName(ROOT_PACKAGE + ".domain." + feature + ".port.inbound." + name);
                Class<?> implementation = Class.forName(ROOT_PACKAGE + ".application." + feature + ".usecase." + name);
                assertThat(input.isInterface()).isTrue();
                assertThat(input.isAssignableFrom(implementation)).isTrue();
                assertThat(implementation.getConstructors()).allSatisfy(constructor ->
                        assertThat(constructor.getParameterTypes()).contains(
                                ar.edu.itba.dps.certification.domain.shared.port.Transactions.class));
            }
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

}
