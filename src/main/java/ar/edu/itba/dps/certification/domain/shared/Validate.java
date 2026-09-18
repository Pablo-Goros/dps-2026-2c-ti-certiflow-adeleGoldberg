package ar.edu.itba.dps.certification.domain.shared;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class Validate {

    private Validate() {
    }

    public static <T> T required(T value, String name) {
        if (value == null) {
            throw new DomainException(name + " is required");
        }
        return value;
    }

    public static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(name + " is required and cannot be blank");
        }
        return value.strip();
    }

    public static List<String> requiredTexts(Collection<String> values, String name) {
        requiredNonEmpty(values, name);
        return values.stream().map(value -> requiredText(value, name + " entry")).toList();
    }

    public static <T> List<T> requiredNonEmpty(Collection<T> values, String name) {
        required(values, name);
        if (values.isEmpty()) {
            throw new DomainException(name + " must contain at least one element");
        }
        return List.copyOf(values);
    }

    public static Map<String, String> requiredTextEntries(Map<String, String> entries, String name) {
        required(entries, name);
        entries.forEach((key, value) -> {
            requiredText(key, name + " name");
            requiredText(value, "value of " + name + " '" + key + "'");
        });
        return Map.copyOf(entries);
    }

    public static int requiredPositive(int value, String name) {
        if (value <= 0) {
            throw new DomainException(name + " must be greater than zero, but was " + value);
        }
        return value;
    }

    public static void ensure(boolean condition, String message) {
        if (!condition) {
            throw new DomainException(message);
        }
    }
}
