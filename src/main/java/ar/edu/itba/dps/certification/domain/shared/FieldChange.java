package ar.edu.itba.dps.certification.domain.shared;

public record FieldChange(String field, String previousValue, String currentValue) {

    public FieldChange {
        field = Validate.requiredText(field, "changed field name");
    }

    public static FieldChange of(String field, Object previous, Object current) {
        return new FieldChange(field, render(previous), render(current));
    }

    private static String render(Object value) {
        return value == null ? null : value.toString();
    }

    @Override
    public String toString() {
        return field + ": " + previousValue + " -> " + currentValue;
    }
}
