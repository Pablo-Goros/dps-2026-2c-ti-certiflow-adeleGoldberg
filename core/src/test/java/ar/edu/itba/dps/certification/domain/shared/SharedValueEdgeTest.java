package ar.edu.itba.dps.certification.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedValueEdgeTest {

    @Test
    @DisplayName("actors and field changes expose human-readable values")
    void actorsAndFieldChangesExposeHumanReadableValues() {
        Actor user = Actor.user(PartyId.of("party-1"), " Ana ");
        Actor system = Actor.system();
        FieldChange change = FieldChange.of("location", null, "Building 2");

        assertThat(user.displayName()).isEqualTo("Ana");
        assertThat(system.displayName()).isEqualTo("system");
        assertThat(change.previousValue()).isNull();
        assertThat(change.currentValue()).isEqualTo("Building 2");
        assertThat(change.toString()).isEqualTo("location: null -> Building 2");
    }

    @Test
    @DisplayName("domain exceptions keep causes and validators reject invalid values")
    void exceptionsAndValidatorsCoverInvalidValues() {
        IllegalStateException cause = new IllegalStateException("root");
        DomainException exception = new DomainException("wrapped", cause);

        assertThat(exception).hasMessage("wrapped").hasCause(cause);
        assertThatThrownBy(() -> Validate.requiredText(null, "name"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("cannot be blank");
        assertThatThrownBy(() -> Validate.requiredText("   ", "name"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("cannot be blank");
        assertThatThrownBy(() -> Validate.requiredPositive(0, "count"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> Validate.requiredTextEntries(Map.of("room", " "), "characteristic"))
                .isInstanceOf(InvalidArgumentException.class)
                .hasMessageContaining("value of characteristic");
    }
}
