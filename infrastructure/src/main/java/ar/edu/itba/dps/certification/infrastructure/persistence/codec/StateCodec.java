package ar.edu.itba.dps.certification.infrastructure.persistence.codec;

import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Converts an aggregate (or any object graph of the domain) to a text document and back,
 * using the object's own state instead of any API the domain would have to expose.
 *
 * <h2>Why it exists</h2>
 * The aggregates keep their state private and their invariants inside their methods. A relational
 * mapping would force constructors, setters or "restore" factories into the domain only to please
 * the database. Instead each aggregate is stored as one document, because the aggregate is the unit
 * of consistency, and this codec rebuilds it from that document the same way an ORM or a
 * serialization framework does: without running constructors, writing the fields directly.
 *
 * <h2>Document format</h2>
 * <ul>
 *   <li>Scalars map to JSON scalars; enums to their name; {@code java.time} values, {@link UUID} and
 *       big numbers to text.</li>
 *   <li>Objects and records map to JSON objects keyed by field name. Static and {@code transient}
 *       fields are not state.</li>
 *   <li>Lists and sets map to arrays; maps to an array of {@code {"k":..,"v":..}} so any key type
 *       works.</li>
 *   <li>When the runtime class differs from the declared type (an interface, a sealed hierarchy, a
 *       subclass) the object carries {@code "@type"} with the class name; a scalar in such a slot is
 *       wrapped as {@code {"@type":..,"@value":..}}.</li>
 * </ul>
 *
 * <h2>Guarantees and limits</h2>
 * Records are rebuilt through their canonical constructor and expose immutable collections;
 * other classes are rebuilt field by field, so a stored document is never re-validated against
 * today's rules. Fields missing in an old document stay at their default (empty for collections),
 * and unknown ones are ignored, which allows additive evolution. Cycles, lambdas, anonymous classes
 * and JDK types outside the supported list fail loudly with the path of the offending field.
 * Class names are part of the format and only classes of this project (plus a short list of JDK
 * scalars) can be instantiated from a document.
 */
public final class StateCodec {

    public static final int FORMAT_VERSION = 1;

    private static final String TYPE = "@type";
    private static final String VALUE = "@value";
    private static final String ENTRY_KEY = "k";
    private static final String ENTRY_VALUE = "v";
    private static final String PROJECT_PACKAGE = "ar.edu.itba.dps.certification.";

    private static final ReflectionFactory REFLECTION = ReflectionFactory.getReflectionFactory();

    private static final Map<Class<?>, Function<String, Object>> TEXTUAL = Map.ofEntries(
            Map.entry(Instant.class, Instant::parse),
            Map.entry(LocalDate.class, LocalDate::parse),
            Map.entry(LocalDateTime.class, LocalDateTime::parse),
            Map.entry(LocalTime.class, LocalTime::parse),
            Map.entry(OffsetDateTime.class, OffsetDateTime::parse),
            Map.entry(Duration.class, Duration::parse),
            Map.entry(Period.class, Period::parse),
            Map.entry(YearMonth.class, YearMonth::parse),
            Map.entry(UUID.class, UUID::fromString),
            Map.entry(BigDecimal.class, BigDecimal::new),
            Map.entry(BigInteger.class, BigInteger::new));

    private static final Set<Class<?>> BOXED_NUMBERS = Set.of(
            Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class);

    private final Map<Class<?>, List<Field>> fieldsByClass = new ConcurrentHashMap<>();
    private final Map<Class<?>, Constructor<?>> blankConstructors = new ConcurrentHashMap<>();

    /** Serializes {@code root} to a JSON document. */
    public String write(Object root) {
        if (root == null) {
            throw new PersistenceException("cannot store a null aggregate");
        }
        return Json.write(encode(root, root.getClass(), "$", new IdentityHashMap<>()));
    }

    /** Rebuilds an object of {@code type} from a document produced by {@link #write(Object)}. */
    public <T> T read(String document, Class<T> type) {
        Object tree = Json.parse(document);
        Object value = decode(tree, type, "$");
        if (value == null) {
            throw new PersistenceException("stored document of " + type.getSimpleName() + " is empty");
        }
        return type.cast(value);
    }

    // ------------------------------------------------------------------ encoding

    private Object encode(Object value, Type declared, String path, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            return null;
        }
        Class<?> declaredRaw = raw(declared);
        if (value instanceof Optional<?> optional) {
            return encode(optional.orElse(null), typeArgument(declared, 0), path, visiting);
        }
        if (value instanceof Collection<?> collection) {
            return encodeCollection(collection, declared, path, visiting);
        }
        if (value instanceof Map<?, ?> map) {
            return encodeMap(map, declared, path, visiting);
        }
        Class<?> runtime = value instanceof Enum<?> constant ? constant.getDeclaringClass() : value.getClass();
        Object scalar = scalarOf(value);
        if (scalar != NOT_A_SCALAR) {
            return typed(scalar, declaredRaw, runtime);
        }
        rejectUnsupported(runtime, path);
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new PersistenceException("cycle in the object graph at " + path);
        }
        try {
            Map<String, Object> node = new LinkedHashMap<>();
            if (needsType(declaredRaw, runtime)) {
                node.put(TYPE, runtime.getName());
            }
            for (Field field : fieldsOf(runtime)) {
                node.put(field.getName(), encode(read(field, value, path), field.getGenericType(),
                        path + "." + field.getName(), visiting));
            }
            return node;
        } finally {
            visiting.remove(value);
        }
    }

    private Object encodeCollection(Collection<?> collection, Type declared, String path,
            IdentityHashMap<Object, Boolean> visiting) {
        Type elementType = typeArgument(declared, 0);
        List<Object> items = new ArrayList<>(collection.size());
        int index = 0;
        for (Object element : collection) {
            items.add(encode(element, elementType, path + "[" + index++ + "]", visiting));
        }
        return items;
    }

    private Object encodeMap(Map<?, ?> map, Type declared, String path, IdentityHashMap<Object, Boolean> visiting) {
        Type keyType = typeArgument(declared, 0);
        Type valueType = typeArgument(declared, 1);
        List<Object> entries = new ArrayList<>(map.size());
        int index = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String entryPath = path + "{" + index++ + "}";
            Map<String, Object> node = new LinkedHashMap<>();
            node.put(ENTRY_KEY, encode(entry.getKey(), keyType, entryPath + ".k", visiting));
            node.put(ENTRY_VALUE, encode(entry.getValue(), valueType, entryPath + ".v", visiting));
            entries.add(node);
        }
        return entries;
    }

    private static final Object NOT_A_SCALAR = new Object();

    private static Object scalarOf(Object value) {
        return switch (value) {
            case String s -> s;
            case Boolean b -> b;
            case Character c -> c.toString();
            case Byte b -> b.longValue();
            case Short s -> s.longValue();
            case Integer i -> i.longValue();
            case Long l -> l;
            case Float f -> f.doubleValue();
            case Double d -> d;
            case Enum<?> e -> e.name();
            case BigDecimal d -> d.toString();
            case BigInteger i -> i.toString();
            default -> TEXTUAL.containsKey(value.getClass()) ? value.toString() : NOT_A_SCALAR;
        };
    }

    private static Object typed(Object scalar, Class<?> declared, Class<?> runtime) {
        if (!needsType(declared, runtime)) {
            return scalar;
        }
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put(TYPE, runtime.getName());
        wrapper.put(VALUE, scalar);
        return wrapper;
    }

    private static boolean needsType(Class<?> declared, Class<?> runtime) {
        if (declared == runtime) {
            return false;
        }
        if (declared.isPrimitive()) {
            return false;
        }
        return !(declared == Object.class && (runtime == String.class || runtime == Boolean.class));
    }

    private void rejectUnsupported(Class<?> type, String path) {
        boolean jdkType = type.getModule().isNamed();
        if (jdkType || type.isAnonymousClass() || type.isLocalClass() || type.isSynthetic()
                || type.isHidden() || type.isArray() || type.isInterface()) {
            throw new PersistenceException("cannot store " + type.getName() + " found at " + path
                    + ": only classes of this project and the supported value types can be part of an aggregate");
        }
    }

    // ------------------------------------------------------------------ decoding

    private Object decode(Object node, Type declared, String path) {
        Class<?> declaredRaw = raw(declared);
        if (declaredRaw == Optional.class) {
            return Optional.ofNullable(decode(node, typeArgument(declared, 0), path));
        }
        if (node == null) {
            return declaredRaw.isPrimitive() ? defaultOf(declaredRaw) : null;
        }
        if (Collection.class.isAssignableFrom(declaredRaw) || declaredRaw == Iterable.class) {
            return decodeCollection(node, declared, declaredRaw, path);
        }
        if (Map.class.isAssignableFrom(declaredRaw)) {
            return decodeMap(node, declared, declaredRaw, path);
        }
        Class<?> runtime = declaredRaw;
        Object content = node;
        if (node instanceof Map<?, ?> map && map.containsKey(TYPE)) {
            runtime = resolve((String) map.get(TYPE), declaredRaw, path);
            content = map.containsKey(VALUE) ? map.get(VALUE) : node;
        }
        return decodeInstance(content, runtime, path);
    }

    private Object decodeInstance(Object content, Class<?> runtime, String path) {
        Object scalar = decodeScalar(content, runtime, path);
        if (scalar != NOT_A_SCALAR) {
            return scalar;
        }
        if (!(content instanceof Map<?, ?> state)) {
            throw new PersistenceException("expected an object for " + runtime.getName() + " at " + path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) state;
        return runtime.isRecord() ? decodeRecord(fields, runtime, path) : decodeObject(fields, runtime, path);
    }

    private Object decodeScalar(Object content, Class<?> type, String path) {
        if (type == String.class || type == Object.class && content instanceof String) {
            return text(content, path);
        }
        if (type == Boolean.class || type == boolean.class || type == Object.class && content instanceof Boolean) {
            return content;
        }
        if (type == Character.class || type == char.class) {
            String text = text(content, path);
            return text.charAt(0);
        }
        Class<?> boxed = box(type);
        if (BOXED_NUMBERS.contains(boxed)) {
            return number(content, boxed, path);
        }
        if (type.isEnum()) {
            return enumConstant(type, text(content, path), path);
        }
        Function<String, Object> parser = TEXTUAL.get(type);
        if (parser != null) {
            try {
                return parser.apply(text(content, path));
            } catch (RuntimeException e) {
                throw new PersistenceException("invalid " + type.getSimpleName() + " at " + path + ": " + content, e);
            }
        }
        return NOT_A_SCALAR;
    }

    private Object decodeCollection(Object node, Type declared, Class<?> declaredRaw, String path) {
        if (!(node instanceof List<?> items)) {
            throw new PersistenceException("expected an array at " + path);
        }
        Type elementType = typeArgument(declared, 0);
        Collection<Object> result = newCollection(declaredRaw, path);
        int index = 0;
        for (Object item : items) {
            result.add(decode(item, elementType, path + "[" + index++ + "]"));
        }
        return result;
    }

    private Object decodeMap(Object node, Type declared, Class<?> declaredRaw, String path) {
        if (!(node instanceof List<?> entries)) {
            throw new PersistenceException("expected an array of entries at " + path);
        }
        if (declaredRaw != Map.class && declaredRaw != LinkedHashMap.class) {
            throw new PersistenceException("unsupported map type " + declaredRaw.getName() + " at " + path);
        }
        Type keyType = typeArgument(declared, 0);
        Type valueType = typeArgument(declared, 1);
        Map<Object, Object> result = new LinkedHashMap<>();
        int index = 0;
        for (Object entry : entries) {
            String entryPath = path + "{" + index++ + "}";
            if (!(entry instanceof Map<?, ?> pair)) {
                throw new PersistenceException("expected an entry object at " + entryPath);
            }
            result.put(decode(pair.get(ENTRY_KEY), keyType, entryPath + ".k"),
                    decode(pair.get(ENTRY_VALUE), valueType, entryPath + ".v"));
        }
        return result;
    }

    private Object decodeRecord(Map<String, Object> state, Class<?> type, String path) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            types[i] = component.getType();
            arguments[i] = immutable(decode(state.get(component.getName()), component.getGenericType(),
                    path + "." + component.getName()));
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(types);
            canonical.setAccessible(true);
            return canonical.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new PersistenceException("stored state of " + type.getSimpleName() + " at " + path
                    + " is rejected by the record: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("cannot rebuild " + type.getName() + " at " + path, e);
        }
    }

    private Object decodeObject(Map<String, Object> state, Class<?> type, String path) {
        Object instance = blank(type, path);
        for (Field field : fieldsOf(type)) {
            String fieldPath = path + "." + field.getName();
            Class<?> fieldType = field.getType();
            Object value;
            if (state.containsKey(field.getName())) {
                value = decode(state.get(field.getName()), field.getGenericType(), fieldPath);
            } else if (Collection.class.isAssignableFrom(fieldType)) {
                value = newCollection(fieldType, fieldPath);
            } else if (Map.class.isAssignableFrom(fieldType)) {
                value = new LinkedHashMap<>();
            } else {
                continue;
            }
            if (value == null && fieldType.isPrimitive()) {
                continue;
            }
            write(field, instance, value, fieldPath);
        }
        return instance;
    }

    private Class<?> resolve(String name, Class<?> declared, String path) {
        boolean allowed = name.startsWith(PROJECT_PACKAGE) || isAllowedJdkScalar(name);
        if (!allowed) {
            throw new PersistenceException("type " + name + " at " + path + " is not allowed in a stored document");
        }
        try {
            Class<?> type = Class.forName(name, false, StateCodec.class.getClassLoader());
            if (!box(declared).isAssignableFrom(type)) {
                throw new PersistenceException("stored type " + name + " at " + path
                        + " is not a " + declared.getName());
            }
            return type;
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("stored type " + name + " at " + path
                    + " no longer exists; the document needs a migration", e);
        }
    }

    private static boolean isAllowedJdkScalar(String name) {
        try {
            Class<?> type = Class.forName(name, false, StateCodec.class.getClassLoader());
            return TEXTUAL.containsKey(type) || BOXED_NUMBERS.contains(type) || type == Character.class
                    || type == String.class || type == Boolean.class;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ reflection helpers

    private List<Field> fieldsOf(Class<?> type) {
        return fieldsByClass.computeIfAbsent(type, this::collectFields);
    }

    private List<Field> collectFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }
        List<Field> fields = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                if (!names.add(field.getName())) {
                    throw new PersistenceException("field name '" + field.getName() + "' is repeated in the hierarchy of "
                            + type.getName() + "; rename it so the stored document is unambiguous");
                }
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return List.copyOf(fields);
    }

    private Object blank(Class<?> type, String path) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new PersistenceException("the stored document does not say which implementation of "
                    + type.getName() + " to build at " + path);
        }
        Constructor<?> constructor = blankConstructors.computeIfAbsent(type, t -> {
            try {
                Constructor<?> c = REFLECTION.newConstructorForSerialization(t, Object.class.getDeclaredConstructor());
                c.setAccessible(true);
                return c;
            } catch (ReflectiveOperationException e) {
                throw new PersistenceException("cannot instantiate " + t.getName(), e);
            }
        });
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("cannot instantiate " + type.getName() + " at " + path, e);
        }
    }

    private static Object read(Field field, Object owner, String path) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new PersistenceException("cannot read " + field + " at " + path, e);
        }
    }

    private static void write(Field field, Object owner, Object value, String path) {
        try {
            field.set(owner, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new PersistenceException("cannot set " + field + " at " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Class<?> declared, String path) {
        if (declared == Set.class || declared == LinkedHashSet.class) {
            return new LinkedHashSet<>();
        }
        if (declared == List.class || declared == Collection.class || declared == Iterable.class
                || declared == ArrayList.class) {
            return new ArrayList<>();
        }
        throw new PersistenceException("unsupported collection type " + declared.getName() + " at " + path);
    }

    private static Object immutable(Object value) {
        return switch (value) {
            case List<?> list -> Collections.unmodifiableList(list);
            case Set<?> set -> Collections.unmodifiableSet(set);
            case Map<?, ?> map -> Collections.unmodifiableMap(map);
            case null, default -> value;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name, String path) {
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("stored constant '" + name + "' at " + path + " no longer exists in "
                    + type.getSimpleName() + "; the document needs a migration", e);
        }
    }

    private static String text(Object content, String path) {
        if (content instanceof String text) {
            return text;
        }
        throw new PersistenceException("expected text at " + path + " but found " + content);
    }

    private static Object number(Object content, Class<?> boxed, String path) {
        if (!(content instanceof Number number)) {
            throw new PersistenceException("expected a number at " + path + " but found " + content);
        }
        if (boxed == Long.class) {
            return number.longValue();
        }
        if (boxed == Integer.class) {
            return Math.toIntExact(number.longValue());
        }
        if (boxed == Short.class) {
            return (short) number.longValue();
        }
        if (boxed == Byte.class) {
            return (byte) number.longValue();
        }
        if (boxed == Float.class) {
            return number.floatValue();
        }
        return number.doubleValue();
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    private static Object defaultOf(Class<?> primitive) {
        return switch (primitive.getName()) {
            case "boolean" -> false;
            case "char" -> '\0';
            case "int" -> 0;
            case "long" -> 0L;
            case "double" -> 0d;
            case "float" -> 0f;
            case "short" -> (short) 0;
            case "byte" -> (byte) 0;
            default -> null;
        };
    }

    private static Class<?> raw(Type type) {
        return switch (type) {
            case Class<?> c -> c;
            case ParameterizedType p -> (Class<?>) p.getRawType();
            case TypeVariable<?> v -> raw(v.getBounds()[0]);
            case WildcardType w -> raw(w.getUpperBounds()[0]);
            case GenericArrayType a -> throw new PersistenceException("arrays are not supported: " + a);
            default -> Object.class;
        };
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
            return parameterized.getActualTypeArguments()[index];
        }
        return Object.class;
    }
}
