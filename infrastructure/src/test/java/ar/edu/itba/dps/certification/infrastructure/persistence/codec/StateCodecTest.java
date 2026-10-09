package ar.edu.itba.dps.certification.infrastructure.persistence.codec;

import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateCodecTest {

    private final StateCodec codec = new StateCodec();

    // ---- fixtures: they live in the project package, the only one a document may instantiate

    enum Level { LOW, HIGH }

    record Scalars(String text, int small, long big, boolean flag, double ratio, BigDecimal exact,
            Instant at, LocalDate day, Duration span, UUID uuid, Level level) {
    }

    sealed interface Shape permits Circle, Square {
    }

    record Circle(int radius) implements Shape {
    }

    record Square(int side) implements Shape {
    }

    record Drawing(Shape main, List<Shape> others) {
    }

    record Key(String name, int index) {
    }

    record Catalogue(Map<Key, List<Integer>> byKey, Set<String> tags, Optional<String> note,
            Optional<Level> level) {
    }

    static class Helper {
        String shout(String text) {
            return text.toUpperCase();
        }
    }

    static class Basket {
        private final List<String> items = new ArrayList<>();
        private final Helper helper = new Helper();
        private int taken;
        private Level level = Level.LOW;

        void add(String item) {
            items.add(item);
        }

        String shoutFirst() {
            return helper.shout(items.getFirst());
        }
    }

    static class Parent {
        private final String name;

        Parent(String name) {
            this.name = name;
        }
    }

    static class Child extends Parent {
        private final int age;

        Child(String name, int age) {
            super(name);
            this.age = age;
        }
    }

    record Slot(Object content) {
    }

    static class Loop {
        private Loop next;
    }

    static class WithLambda {
        private final Runnable action = () -> {
        };
    }

    record Moved(String old) {
    }

    // ---- tests

    @Test
    @DisplayName("scalars, java.time values, big numbers and enums survive a round trip")
    void scalarsRoundTrip() {
        Scalars original = new Scalars("a \"quoted\"\nline", 7, 9_000_000_000L, true, 0.25,
                new BigDecimal("12.50"), Instant.parse("2026-03-01T10:15:30.123456Z"),
                LocalDate.parse("2026-12-31"), Duration.ofMinutes(90), UUID.randomUUID(), Level.HIGH);

        Scalars copy = codec.read(codec.write(original), Scalars.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.exact().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("an object in a slot of a wider type carries its class, so the right one comes back")
    void polymorphicValuesKeepTheirType() {
        Drawing original = new Drawing(new Circle(3), List.of(new Square(1), new Circle(2)));

        String document = codec.write(original);
        Drawing copy = codec.read(document, Drawing.class);

        assertThat(document).contains("@type");
        assertThat(copy).isEqualTo(original);
        assertThat(copy.main()).isInstanceOf(Circle.class);
    }

    @Test
    @DisplayName("maps with composite keys, sets and optionals round trip")
    void collectionsAndOptionalsRoundTrip() {
        Map<Key, List<Integer>> byKey = new LinkedHashMap<>();
        byKey.put(new Key("b", 2), List.of(1, 2, 3));
        byKey.put(new Key("a", 1), List.of());
        Catalogue original = new Catalogue(byKey, Set.of("x"), Optional.of("remember"), Optional.empty());

        Catalogue copy = codec.read(codec.write(original), Catalogue.class);

        assertThat(copy).isEqualTo(original);
        assertThat(List.copyOf(copy.byKey().keySet())).containsExactly(new Key("b", 2), new Key("a", 1));
    }

    @Test
    @DisplayName("collections inside a record come back immutable, as a record promises")
    void recordsExposeImmutableCollections() {
        Drawing copy = codec.read(codec.write(new Drawing(new Circle(1), List.of(new Square(2)))), Drawing.class);

        assertThatThrownBy(() -> copy.others().add(new Square(9))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("an object is rebuilt field by field, including state its constructor and initializers would set")
    void objectsAreRebuiltWithoutRunningTheirConstructor() {
        Basket original = new Basket();
        original.add("apple");
        original.taken = 4;
        original.level = Level.HIGH;

        Basket copy = codec.read(codec.write(original), Basket.class);

        assertThat(copy.items).containsExactly("apple");
        assertThat(copy.taken).isEqualTo(4);
        assertThat(copy.level).isEqualTo(Level.HIGH);
        assertThat(copy.shoutFirst()).isEqualTo("APPLE");
        copy.add("pear");
        assertThat(copy.items).containsExactly("apple", "pear");
    }

    @Test
    @DisplayName("inherited fields are part of the state")
    void inheritedFieldsAreStored() {
        Child copy = codec.read(codec.write(new Child("Ana", 30)), Child.class);

        assertThat(copy.age).isEqualTo(30);
        assertThat(((Parent) copy).name).isEqualTo("Ana");
    }

    @Test
    @DisplayName("scalars in an Object slot keep their exact type")
    void scalarsInWideSlotsKeepTheirType() {
        assertThat(codec.read(codec.write(new Slot(5)), Slot.class).content()).isEqualTo(5);
        assertThat(codec.read(codec.write(new Slot(5L)), Slot.class).content()).isEqualTo(5L);
        assertThat(codec.read(codec.write(new Slot("text")), Slot.class).content()).isEqualTo("text");
        assertThat(codec.read(codec.write(new Slot(true)), Slot.class).content()).isEqualTo(true);
        assertThat(codec.read(codec.write(new Slot(Level.HIGH)), Slot.class).content()).isEqualTo(Level.HIGH);
    }

    @Test
    @DisplayName("a document written before a field existed still loads: collections are empty, the rest default")
    void missingFieldsTakeDefaultsAndUnknownOnesAreIgnored() {
        Basket copy = codec.read("{\"taken\":2,\"removedLongAgo\":\"x\"}", Basket.class);

        assertThat(copy.items).isEmpty();
        assertThat(copy.taken).isEqualTo(2);
        assertThat(copy.level).isNull();
    }

    @Test
    @DisplayName("writing the same state twice gives the same document")
    void documentsAreDeterministic() {
        Drawing drawing = new Drawing(new Circle(3), List.of(new Square(1)));

        assertThat(codec.write(drawing)).isEqualTo(codec.write(drawing));
        assertThat(codec.write(codec.read(codec.write(drawing), Drawing.class))).isEqualTo(codec.write(drawing));
    }

    @Test
    @DisplayName("a cycle is reported with the path where it closes")
    void cyclesAreRejected() {
        Loop loop = new Loop();
        loop.next = new Loop();
        loop.next.next = loop;

        assertThatThrownBy(() -> codec.write(loop))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("cycle")
                .hasMessageContaining("$.next.next");
    }

    @Test
    @DisplayName("lambdas and types outside the project cannot be stored")
    void unsupportedTypesAreRejectedWithTheirPath() {
        assertThatThrownBy(() -> codec.write(new WithLambda()))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.action");
        assertThatThrownBy(() -> codec.write(new Slot(new Object())))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.content");
    }

    @Test
    @DisplayName("a document cannot make the codec instantiate a class that is not part of the project")
    void onlyProjectClassesCanBeInstantiated() {
        String hostile = "{\"content\":{\"@type\":\"java.lang.ProcessBuilder\"}}";

        assertThatThrownBy(() -> codec.read(hostile, Slot.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("stored names that no longer exist ask for a migration instead of failing obscurely")
    void removedClassesAndConstantsAreReportedClearly() {
        assertThatThrownBy(() -> codec.read("{\"content\":{\"@type\":\"ar.edu.itba.dps.certification.Gone\"}}", Slot.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("needs a migration");
        assertThatThrownBy(() -> codec.read("{\"level\":\"EXTINCT\"}", Catalogue.class))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("malformed text is a persistence error, not a parser exception")
    void malformedDocumentsAreReported() {
        assertThatThrownBy(() -> codec.read("{\"taken\":", Basket.class)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> codec.read("[1,2", Basket.class)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> codec.read("{} trailing", Basket.class)).isInstanceOf(PersistenceException.class);
    }
}
