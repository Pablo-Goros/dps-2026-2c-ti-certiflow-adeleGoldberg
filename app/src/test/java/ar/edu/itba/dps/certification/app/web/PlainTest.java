package ar.edu.itba.dps.certification.app.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTest {

    record Id(String value) {
    }

    sealed interface Shape permits Circle, Square {
    }

    record Circle(int radius) implements Shape {
    }

    record Square(int side) implements Shape {
    }

    enum Color { RED }

    record Holder(Id id, Optional<Id> maybe, Optional<Id> none, Color color, Instant at, List<Shape> shapes,
            Map<Id, Integer> counts) {
    }

    @Test
    void identifierRecordsBecomeTheirText() {
        assertThat(Plain.of(new Id("abc"))).isEqualTo("abc");
    }

    @Test
    void recordsBecomeMapsAndOptionalsAreUnwrapped() {
        Object plain = Plain.of(new Holder(new Id("1"), Optional.of(new Id("2")), Optional.empty(), Color.RED,
                Instant.parse("2026-01-01T00:00:00Z"), List.of(), Map.of(new Id("k"), 3)));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) plain;
        assertThat(map.get("id")).isEqualTo("1");
        assertThat(map.get("maybe")).isEqualTo("2");
        assertThat(map.get("none")).isNull();
        assertThat(map.get("color")).isEqualTo("RED");
        assertThat(map.get("at")).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(map.get("counts")).isEqualTo(Map.of("k", 3));
    }

    @Test
    void variantsOfASealedInterfaceCarryTheirName() {
        Object plain = Plain.of(List.of(new Circle(2), new Square(3)));

        assertThat(plain).isEqualTo(List.of(
                Map.of("type", "Circle", "radius", 2),
                Map.of("type", "Square", "side", 3)));
    }
}
