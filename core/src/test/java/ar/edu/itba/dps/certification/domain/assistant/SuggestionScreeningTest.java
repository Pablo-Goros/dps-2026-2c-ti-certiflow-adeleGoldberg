package ar.edu.itba.dps.certification.domain.assistant;

import ar.edu.itba.dps.certification.domain.catalogue.AssetType;
import ar.edu.itba.dps.certification.domain.schema.CriterionId;
import ar.edu.itba.dps.certification.domain.schema.CriterionResult;
import ar.edu.itba.dps.certification.domain.schema.Severity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The rule that decides what the domain keeps from an assistant's answer, without any inspection. */
class SuggestionScreeningTest {

    private static final CriterionId ELEC = CriterionId.of("ELEC");
    private static final CriterionId PRES = CriterionId.of("PRES");

    private static final AssistanceRequest REQUEST = new AssistanceRequest(AssetType.FACILITY, Set.of(),
            List.of(criterion(ELEC), criterion(PRES)),
            List.of(new Observation(Optional.empty(), "cable pelado junto al tablero")));

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("a finding the domain cannot keep is refused with its reason")
    void refusesWhatTheDomainCannotKeep(String situation, SuggestedFinding finding, String reason) {
        var screened = SuggestionScreening.screen(REQUEST, List.of(finding));

        assertThat(screened.accepted()).isEmpty();
        assertThat(screened.rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo(reason));
    }

    static Stream<Arguments> refusals() {
        return Stream.of(
                Arguments.of("a criterion outside the consultation",
                        finding(CriterionId.of("SAFE"), CriterionResult.REJECTED, "exit blocked", "seen"),
                        "criterion SAFE was not part of the consultation"),
                Arguments.of("an approving result",
                        finding(ELEC, CriterionResult.APPROVED, "fine", "seen"),
                        "an approving result is not a finding"),
                Arguments.of("an empty description",
                        finding(ELEC, CriterionResult.REJECTED, " ", "seen"),
                        "a suggestion needs a description and a rationale"),
                Arguments.of("an empty rationale",
                        finding(ELEC, CriterionResult.REJECTED, "exposed conductor", ""),
                        "a suggestion needs a description and a rationale"));
    }

    @Test
    @DisplayName("keeps valid findings in order and refuses a second one for the same criterion")
    void keepsValidFindingsAndOnlyOnePerCriterion() {
        var first = finding(ELEC, CriterionResult.REJECTED, "exposed conductor", "the note describes it");
        var second = finding(PRES, CriterionResult.OBSERVED, "label faded", "the note mentions it");
        var again = finding(ELEC, CriterionResult.OBSERVED, "worn insulation", "again");

        var screened = SuggestionScreening.screen(REQUEST, List.of(first, again, second));

        assertThat(screened.accepted()).containsExactly(first, second);
        assertThat(screened.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.output()).isEqualTo(again);
            assertThat(rejected.reason()).isEqualTo("criterion ELEC was already suggested in this consultation");
        });
    }

    @Test
    @DisplayName("an empty answer is a valid answer: nothing kept, nothing refused")
    void anEmptyAnswerIsValid() {
        var screened = SuggestionScreening.screen(REQUEST, List.of());

        assertThat(screened.accepted()).isEmpty();
        assertThat(screened.rejected()).isEmpty();
    }

    private static CriterionContext criterion(CriterionId id) {
        return new CriterionContext(id, "Section", Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
                List.of(), List.of(new OutcomeOption("BAD", CriterionResult.REJECTED, Optional.of(Severity.HIGH), "bad")));
    }

    private static SuggestedFinding finding(CriterionId id, CriterionResult result, String description, String rationale) {
        return new SuggestedFinding(id, result, Severity.HIGH, description, rationale);
    }
}
