package ar.edu.itba.dps.certification.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The packaged application serves the React front end itself, next to the API. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:certiflow-frontend-it;DB_CLOSE_DELAY=-1")
class FrontendIT extends ApiTest {

    @Test
    void theRootPageIsTheFrontEnd() {
        var reply = api.get("/");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).contains("<div id=\"root\">").contains("CertiFlow");
    }

    @Test
    void theBuiltScriptsAndStylesAreServed() {
        String page = api.get("/").body();
        var asset = java.util.regex.Pattern.compile("/assets/[^\"]+\\.js").matcher(page);

        assertThat(asset.find()).as("the page references its script").isTrue();
        assertThat(api.get(asset.group()).status()).isEqualTo(200);
    }

    @Test
    void theApiKeepsAnsweringJsonNextToTheStaticPages() {
        var reply = api.get("/api/meta/jurisdictions");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).startsWith("[");
    }
}
