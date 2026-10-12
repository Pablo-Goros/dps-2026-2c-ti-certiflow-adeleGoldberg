package ar.edu.itba.dps.certification.infrastructure.persistence.codec;

import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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

    record ExtraScalars(char letter, byte tiny, short small, float ratio, Period period,
            YearMonth month, LocalTime time, OffsetDateTime offset) {
    }

    record PrimitiveDefaults(boolean flag, char letter, int number, long big, double ratio,
            float decimal, short small, byte tiny) {
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

    record TextSlot(String content) {
    }

    record QueueHolder(Queue<String> queue) {
    }

    record HashtableHolder(Hashtable<String, String> values) {
    }

    static class SameNameParent {
        private final String value = "parent";
    }

    static class SameNameChild extends SameNameParent {
        private final String value = "child";
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
    @DisplayName("less common scalar types survive a round trip")
    void extraScalarsRoundTrip() {
        ExtraScalars original = new ExtraScalars('ñ', (byte) 3, (short) 7, 0.5f,
                Period.of(1, 2, 3), YearMonth.parse("2026-10"), LocalTime.parse("12:34:56"),
                OffsetDateTime.parse("2026-10-12T19:30:00-03:00"));

        ExtraScalars copy = codec.read(codec.write(original), ExtraScalars.class);

        assertThat(copy).isEqualTo(original);
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
        assertThat(codec.read(codec.write(new Slot('x')), Slot.class).content()).isEqualTo('x');
        assertThat(codec.read(codec.write(new Slot((byte) 2)), Slot.class).content()).isEqualTo((byte) 2);
        assertThat(codec.read(codec.write(new Slot((short) 4)), Slot.class).content()).isEqualTo((short) 4);
        assertThat(codec.read(codec.write(new Slot(1.5f)), Slot.class).content()).isEqualTo(1.5f);
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
    @DisplayName("missing primitive fields in an old record use their JVM defaults")
    void missingPrimitiveFieldsUseDefaults() {
        PrimitiveDefaults copy = codec.read("{}", PrimitiveDefaults.class);

        assertThat(copy.flag()).isFalse();
        assertThat(copy.letter()).isEqualTo('\0');
        assertThat(copy.number()).isZero();
        assertThat(copy.big()).isZero();
        assertThat(copy.ratio()).isZero();
        assertThat(copy.decimal()).isZero();
        assertThat(copy.small()).isZero();
        assertThat(copy.tiny()).isZero();
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
        assertThatThrownBy(() -> codec.write(new Slot(new int[] {1, 2})))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.content");
        assertThatThrownBy(() -> codec.write(new SameNameChild()))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("repeated in the hierarchy");
    }

    @Test
    @DisplayName("a document cannot make the codec instantiate a class that is not part of the project")
    void onlyProjectClassesCanBeInstantiated() {
        String hostile = "{\"content\":{\"@type\":\"java.lang.ProcessBuilder\"}}";

        assertThatThrownBy(() -> codec.read(hostile, Slot.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> codec.read("{\"content\":{\"@type\":\"java.lang.Integer\",\"@value\":1}}",
                TextSlot.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("is not a java.lang.String");
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
    @DisplayName("documents with a wrong shape are rejected at the exact field path")
    void wrongDocumentShapesAreReported() {
        assertThatThrownBy(() -> codec.read("null", Basket.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> codec.read("[]", Basket.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("expected an object");
        assertThatThrownBy(() -> codec.read("{\"items\":\"not-an-array\"}", Basket.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.items");
        assertThatThrownBy(() -> codec.read("{\"byKey\":{}}", Catalogue.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.byKey");
        assertThatThrownBy(() -> codec.read("{\"byKey\":[1]}", Catalogue.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("$.");
        assertThatThrownBy(() -> codec.read("{\"level\":7}", Catalogue.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("expected text");
        assertThatThrownBy(() -> codec.read("{\"at\":\"not-an-instant\"}", Scalars.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("invalid Instant");
    }

    @Test
    @DisplayName("unsupported declared collection and map types fail clearly while rebuilding")
    void unsupportedDeclaredContainersAreRejected() {
        assertThatThrownBy(() -> codec.read("{\"queue\":[]}", QueueHolder.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("unsupported collection type");
        assertThatThrownBy(() -> codec.read("{\"values\":[]}", HashtableHolder.class))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("unsupported map type");
    }

    @Test
    @DisplayName("malformed text is a persistence error, not a parser exception")
    void malformedDocumentsAreReported() {
        assertThatThrownBy(() -> codec.read("{\"taken\":", Basket.class)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> codec.read("[1,2", Basket.class)).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> codec.read("{} trailing", Basket.class)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("the owned JSON layer covers escaping, arrays, objects and malformed edge cases")
    void jsonLayerHandlesEdgesExplicitly() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("escaped", "\"\\\b\f\n\r\t\u0001");
        List<Object> values = new ArrayList<>();
        values.add(null);
        values.add(true);
        values.add(false);
        values.add(1L);
        values.add(2.5d);
        values.add(new LinkedHashMap<>());
        tree.put("values", values);

        Object parsed = Json.parse(Json.write(tree));

        assertThat(parsed).isEqualTo(tree);
        assertThatThrownBy(() -> Json.write(Double.NaN))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("non finite");
        assertThatThrownBy(() -> Json.write(new HashMap<>(Map.of("x", new Object()))))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("not a state tree node");
        assertThatThrownBy(() -> Json.parse("{x:1}")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("{\"x\" 1}")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("[1;2]")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("\"\\q\"")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("\"\\u12\"")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("tru")).isInstanceOf(PersistenceException.class);
        assertThatThrownBy(() -> Json.parse("@")).isInstanceOf(PersistenceException.class);
    }
}
