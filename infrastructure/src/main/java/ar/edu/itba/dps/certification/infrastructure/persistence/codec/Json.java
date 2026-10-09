package ar.edu.itba.dps.certification.infrastructure.persistence.codec;

import ar.edu.itba.dps.certification.infrastructure.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON text layer for the state trees produced by {@link StateCodec}.
 *
 * <p>A tree is made only of {@code null}, {@link Boolean}, {@link Long}, {@link Double},
 * {@link String}, {@link List} and {@link Map} with {@link String} keys. Keeping this layer tiny
 * and owned by the project means the stored format cannot drift because of a library upgrade or
 * a mapper configuration.
 */
final class Json {

    private Json() {
    }

    static String write(Object tree) {
        StringBuilder out = new StringBuilder(256);
        writeValue(tree, out);
        return out.toString();
    }

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object tree = parser.value();
        parser.expectEnd();
        return tree;
    }

    private static void writeValue(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b);
            case Long l -> out.append(l.longValue());
            case Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    throw new PersistenceException("cannot store a non finite number: " + d);
                }
                out.append(d.doubleValue());
            }
            case String s -> writeString(s, out);
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    writeValue(list.get(i), out);
                }
                out.append(']');
            }
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString((String) entry.getKey(), out);
                    out.append(':');
                    writeValue(entry.getValue(), out);
                }
                out.append('}');
            }
            default -> throw new PersistenceException(
                    "not a state tree node: " + value.getClass().getName());
        }
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {

        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            skipBlanks();
            if (position >= text.length()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        void expectEnd() {
            skipBlanks();
            if (position != text.length()) {
                throw error("unexpected trailing content");
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++;
            skipBlanks();
            if (peek() == '}') {
                position++;
                return result;
            }
            while (true) {
                skipBlanks();
                if (peek() != '"') {
                    throw error("object key expected");
                }
                String key = string();
                skipBlanks();
                expect(':');
                result.put(key, value());
                skipBlanks();
                char next = next();
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    position--;
                    throw error("',' or '}' expected");
                }
            }
        }

        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            position++;
            skipBlanks();
            if (peek() == ']') {
                position++;
                return result;
            }
            while (true) {
                result.add(value());
                skipBlanks();
                char next = next();
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    position--;
                    throw error("',' or ']' expected");
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (position + 4 > text.length()) {
                            throw error("incomplete unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("invalid escape \\" + escaped);
                }
            }
        }

        private Object number() {
            int start = position;
            boolean integral = true;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == '.' || c == 'e' || c == 'E') {
                    integral = false;
                } else if (!(c == '-' || c == '+' || (c >= '0' && c <= '9'))) {
                    break;
                }
                position++;
            }
            String token = text.substring(start, position);
            if (token.isEmpty()) {
                throw error("value expected");
            }
            try {
                return integral ? (Object) Long.parseLong(token) : (Object) Double.parseDouble(token);
            } catch (NumberFormatException e) {
                position = start;
                throw error("invalid number '" + token + "'");
            }
        }

        private Object literal(String word, Object result) {
            if (!text.startsWith(word, position)) {
                throw error("unexpected token");
            }
            position += word.length();
            return result;
        }

        private void skipBlanks() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (position >= text.length()) {
                throw error("unexpected end of input");
            }
            return text.charAt(position);
        }

        private char next() {
            char c = peek();
            position++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                position--;
                throw error("'" + expected + "' expected");
            }
        }

        private PersistenceException error(String message) {
            return new PersistenceException("malformed stored state at character " + position + ": " + message);
        }
    }
}
