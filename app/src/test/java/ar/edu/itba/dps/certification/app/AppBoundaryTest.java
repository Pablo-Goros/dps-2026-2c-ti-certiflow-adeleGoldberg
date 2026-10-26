package ar.edu.itba.dps.certification.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the pieces may touch each other inside the application module (source scan):
 * only {@code config} knows the infrastructure adapters; the web layer reaches the core through use
 * domain input ports and reference-data contracts, never through application implementations.
 */
class AppBoundaryTest {

    private static final Path APP = Path.of("src", "main", "java", "ar", "edu", "itba", "dps", "certification", "app");
    private static final String ROOT_PACKAGE = "ar.edu.itba.dps.certification";
    private static final Pattern DRIVEN_PORT_IMPORT = Pattern.compile(
            "import " + Pattern.quote(ROOT_PACKAGE) + "\\.domain\\.[a-z]+\\.port\\.(?:\\w*Repository|FindingQuery|InspectionQuery|FindingRegistry|SchemaCatalog|Transactions);");

    @Test
    void onlyTheConfigurationKnowsTheInfrastructure() throws IOException {
        for (String layer : List.of("web", "jobs")) {
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

    @Test
    void controllersDependOnlyOnDomainInterfacesAndDoNotOpenTransactions() throws Exception {
        for (String name : List.of("Asset", "Party", "Schema", "Inspection", "Finding", "Certificate", "Audit", "Admin", "Meta")) {
            Class<?> controller = Class.forName(ROOT_PACKAGE + ".app.web." + name + "Controller");
            for (var constructor : controller.getDeclaredConstructors()) {
                for (Class<?> dependency : constructor.getParameterTypes()) {
                    assertThat(dependency.isInterface()).as(dependency.getName()).isTrue();
                    assertThat(dependency.getPackageName()).startsWith(ROOT_PACKAGE + ".domain.");
                }
            }
        }
        assertThat(sourcesOf("web")).allSatisfy(source -> assertThat(source.text())
                .doesNotContain("import " + ROOT_PACKAGE + ".application.", "transactions.execute"));
        assertThat(sourcesOf("jobs")).allSatisfy(source -> assertThat(source.text()).doesNotContain("transactions.execute"));
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
