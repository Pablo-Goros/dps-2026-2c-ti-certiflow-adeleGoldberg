package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the pieces may touch each other inside the application module (source scan):
 * only {@code config} knows the infrastructure adapters; the web layer reaches the core through use
 * cases (and the transaction/clock ports), never through repositories or other driven ports.
 */
class AppBoundaryTest {

    private static final Path APP = Path.of("src", "main", "java", "ar", "edu", "itba", "dps", "certification", "app");
    private static final String ROOT_PACKAGE = "ar.edu.itba.dps.certification";
    private static final Pattern DRIVEN_PORT_IMPORT = Pattern.compile(
            "import " + Pattern.quote(ROOT_PACKAGE) + "\\.application\\.[a-z]+\\.port\\.(?!Transactions;|Clock;)\\w+;");

    @Test
    void onlyTheConfigurationKnowsTheInfrastructure() throws IOException {
        for (String layer : List.of("web", "jobs", "ops")) {
            assertThat(sourcesOf(layer))
                    .as("the " + layer + " package must not import the infrastructure")
                    .noneMatch(source -> source.text().contains("import " + ROOT_PACKAGE + ".infrastructure."))
                    .allSatisfy(source -> assertThat(source.text()).doesNotContain("org.flywaydb"));
        }
    }

    @Test
    void theWebLayerReachesTheCoreThroughUseCasesOnly() throws IOException {
        assertThat(sourcesOf("web"))
                .as("controllers must not import repositories or other driven ports")
                .noneMatch(source -> DRIVEN_PORT_IMPORT.matcher(source.text()).find());
    }

    private record Source(Path file, String text) {
    }

    private static List<Source> sourcesOf(String layer) throws IOException {
        try (var files = Files.walk(APP.resolve(layer))) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> {
                        try {
                            return new Source(file, Files.readString(file));
                        } catch (IOException e) {
                            throw new IllegalStateException("cannot read " + file, e);
                        }
                    })
                    .toList();
        }
    }
}
