package ar.edu.itba.dps.certification.app.web;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the immutable report values of the core (records, ids, optionals, sealed variants) into
 * plain maps, lists and scalars so they serialize as readable JSON: identifier records such as
 * {@code PartyId} become their text, optionals become the value or null, and each variant of a
 * sealed interface carries a {@code type} field naming it.
 */
final class Plain {

    private Plain() {
    }

    static Object of(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof TemporalAccessor || value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(Plain::of).orElse(null);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            collection.forEach(item -> list.add(of(item)));
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(of(key)), of(item)));
            return converted;
        }
        if (value.getClass().isRecord()) {
            return record(value);
        }
        return value.toString();
    }

    private static Object record(Object value) {
        Class<?> type = value.getClass();
        RecordComponent[] components = type.getRecordComponents();
        boolean variant = isSealedVariant(type);
        if (!variant && components.length == 1 && components[0].getName().equals("value")) {
            return of(read(components[0], value));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (variant) {
            fields.put("type", type.getSimpleName());
        }
        for (RecordComponent component : components) {
            fields.put(component.getName(), of(read(component, value)));
        }
        return fields;
    }

    private static boolean isSealedVariant(Class<?> type) {
        for (Class<?> parent : type.getInterfaces()) {
            if (parent.isSealed()) {
                return true;
            }
        }
        return false;
    }

    private static Object read(RecordComponent component, Object target) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read " + component.getName(), e);
        }
    }
}
