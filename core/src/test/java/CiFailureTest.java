import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/** Temporary probe: removed after verifying GitHub blocks this pull request. */
class CiFailureTest {
    @Test
    void intentionalFailureBlocksThePullRequest() {
        fail("Intentional CI validation failure; do not merge this test");
    }
}
