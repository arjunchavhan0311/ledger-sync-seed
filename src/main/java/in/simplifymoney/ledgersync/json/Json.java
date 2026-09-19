package in.simplifymoney.ledgersync.json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSON reader/writer.
 *
 * The project intentionally uses a JDK-only JSON implementation so that
 * the seed project does not need an external JSON dependency.
 *
 * Important for this assignment:
 *
 * - JSON numbers are parsed as BigDecimal.
 * - Money is therefore never converted through double.
 * - Object insertion order is preserved.
 * - Arrays are represented as List<Object>.
 * - JSON objects are represented as Map<String,Object>.
 */
public final class Json {

    private Json() {
    }

    // ================================================================
    // READ
    // ================================================================

    /**
     * Parses any valid JSON value.
     *
     * Supported JSON values:
     *
     *     object
     *     array
     *     string
     *     number
     *     true
     *     false
     *     null
     */
    public static Object parse(String s) {

        if (s == null) {
            throw new IllegalArgumentException("JSON input must not be null");
        }

        Parser parser = new Parser(s);

        parser.ws();

        if (parser.i >= parser.s.length()) {
            throw parser.err("empty JSON input");
        }

        Object value = parser.value();

        parser.ws();

        if (parser.i != parser.s.length()) {
            throw parser.err("trailing content");
        }

        return value;
    }

    /**
     * Parses a JSON object.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String s) {

        Object value = parse(s);

        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "JSON value is not an object"
            );
        }

        return (Map<String, Object>) value;
    }

    // ================================================================
    // PARSER
    // ================================================================

    private static final class Parser {

        private final String s;
        private int i;

        private Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        private RuntimeException err(String message) {
            return new IllegalArgumentException(
                    "JSON at index " + i + ": " + message
            );
        }

        /**
         * Consumes JSON whitespace.
         */
        private void ws() {

            while (i < s.length()) {

                char c = s.charAt(i);

                if (c == ' '
                        || c == '\t'
                        || c == '\n'
                        || c == '\r') {

                    i++;

                } else {
                    break;
                }
            }
        }

        /**
         * Reads one JSON value.
         */
        private Object value() {

            ws();

            if (i >= s.length()) {
                throw err("unexpected end of input");
            }

            char c = s.charAt(i);

            return switch (c) {

                case '{' -> object();

                case '[' -> array();

                case '"' -> string();

                case 't' -> {
                    expect("true");
                    yield Boolean.TRUE;
                }

                case 'f' -> {
                    expect("false");
                    yield Boolean.FALSE;
                }

                case 'n' -> {
                    expect("null");
                    yield null;
                }

                default -> number();
            };
        }

        /**
         * Reads a literal such as true, false or null.
         */
        private void expect(String literal) {

            if (!s.startsWith(literal, i)) {
                throw err("expected '" + literal + "'");
            }

            i += literal.length();
        }

        // ============================================================
        // OBJECT
        // ============================================================

        private Map<String, Object> object() {

            Map<String, Object> result =
                    new LinkedHashMap<>();

            /*
             * {
             */
            i++;

            ws();

            /*
             * Empty object:
             *
             * {}
             */
            if (i < s.length()
                    && s.charAt(i) == '}') {

                i++;
                return result;
            }

            while (true) {

                ws();

                /*
                 * Object keys must always be strings.
                 */
                if (i >= s.length()
                        || s.charAt(i) != '"') {

                    throw err(
                            "expected string object key"
                    );
                }

                String key = string();

                ws();

                if (i >= s.length()
                        || s.charAt(i) != ':') {

                    throw err("expected ':' after object key");
                }

                i++;

                ws();

                Object value = value();

                result.put(key, value);

                ws();

                if (i >= s.length()) {
                    throw err("unterminated object");
                }

                char separator = s.charAt(i++);

                if (separator == '}') {
                    return result;
                }

                if (separator != ',') {
                    throw err("expected ',' or '}'");
                }

                ws();

                /*
                 * JSON does not allow:
                 *
                 * {"a":1,}
                 */
                if (i < s.length()
                        && s.charAt(i) == '}') {

                    throw err("trailing comma in object");
                }
            }
        }

        // ============================================================
        // ARRAY
        // ============================================================

        private List<Object> array() {

            List<Object> result =
                    new ArrayList<>();

            /*
             * [
             */
            i++;

            ws();

            /*
             * Empty array:
             *
             * []
             */
            if (i < s.length()
                    && s.charAt(i) == ']') {

                i++;
                return result;
            }

            while (true) {

                ws();

                result.add(value());

                ws();

                if (i >= s.length()) {
                    throw err("unterminated array");
                }

                char separator = s.charAt(i++);

                if (separator == ']') {
                    return result;
                }

                if (separator != ',') {
                    throw err("expected ',' or ']'");
                }

                ws();

                /*
                 * JSON does not allow:
                 *
                 * [1,2,]
                 */
                if (i < s.length()
                        && s.charAt(i) == ']') {

                    throw err("trailing comma in array");
                }
            }
        }

        // ============================================================
        // STRING
        // ============================================================

        private String string() {

            if (i >= s.length()
                    || s.charAt(i) != '"') {

                throw err("expected string");
            }

            i++;

            StringBuilder result =
                    new StringBuilder();

            while (true) {

                if (i >= s.length()) {
                    throw err("unterminated string");
                }

                char c = s.charAt(i++);

                /*
                 * Closing quote.
                 */
                if (c == '"') {
                    return result.toString();
                }

                /*
                 * JSON strings cannot contain raw control characters.
                 */
                if (c < 0x20) {
                    throw err(
                            "unescaped control character in string"
                    );
                }

                /*
                 * Normal character.
                 */
                if (c != '\\') {
                    result.append(c);
                    continue;
                }

                /*
                 * Escape sequence.
                 */
                if (i >= s.length()) {
                    throw err("unfinished escape sequence");
                }

                char escape = s.charAt(i++);

                switch (escape) {

                    case '"' -> result.append('"');

                    case '\\' -> result.append('\\');

                    case '/' -> result.append('/');

                    case 'b' -> result.append('\b');

                    case 'f' -> result.append('\f');

                    case 'n' -> result.append('\n');

                    case 'r' -> result.append('\r');

                    case 't' -> result.append('\t');

                    case 'u' -> result.append(readUnicodeEscape());

                    default -> throw err(
                            "unknown escape sequence \\" + escape
                    );
                }
            }
        }

        private char readUnicodeEscape() {

            if (i + 4 > s.length()) {
                throw err("incomplete unicode escape");
            }

            int value = 0;

            for (int n = 0; n < 4; n++) {

                char c = s.charAt(i++);

                int digit = Character.digit(c, 16);

                if (digit < 0) {
                    throw err(
                            "invalid hexadecimal digit in unicode escape"
                    );
                }

                value = (value << 4) | digit;
            }

            return (char) value;
        }

        // ============================================================
        // NUMBER
        // ============================================================

        /**
         * Parses a JSON number according to the JSON grammar.
         *
         * Examples:
         *
         *     0
         *     10
         *     -10
         *     10.50
         *     1.25e2
         *     -3.5E-2
         *
         * Numbers are returned as BigDecimal.
         */
        private BigDecimal number() {

            int start = i;

            /*
             * Optional minus.
             */
            if (peek('-')) {
                i++;
            }

            /*
             * Integer part.
             *
             * JSON allows either:
             *
             *     0
             *
             * or:
             *
             *     [1-9][0-9]*
             */
            if (i >= s.length()) {
                throw err("invalid number");
            }

            if (peek('0')) {

                i++;

                /*
                 * Leading zeroes are not allowed:
                 *
                 * 01
                 */
                if (i < s.length()
                        && Character.isDigit(
                        s.charAt(i))) {

                    throw err(
                            "leading zero in number"
                    );
                }

            } else if (isDigitOneToNine(
                    s.charAt(i))) {

                i++;

                while (i < s.length()
                        && Character.isDigit(
                        s.charAt(i))) {

                    i++;
                }

            } else {

                throw err("expected number");
            }

            /*
             * Optional fraction.
             *
             * 10.50
             */
            if (peek('.')) {

                i++;

                int fractionStart = i;

                while (i < s.length()
                        && Character.isDigit(
                        s.charAt(i))) {

                    i++;
                }

                if (fractionStart == i) {
                    throw err(
                            "expected digits after decimal point"
                    );
                }
            }

            /*
             * Optional exponent.
             *
             * 1e10
             * 1E-10
             */
            if (peek('e') || peek('E')) {

                i++;

                if (peek('+') || peek('-')) {
                    i++;
                }

                int exponentStart = i;

                while (i < s.length()
                        && Character.isDigit(
                        s.charAt(i))) {

                    i++;
                }

                if (exponentStart == i) {
                    throw err(
                            "expected digits in exponent"
                    );
                }
            }

            String raw = s.substring(start, i);

            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException e) {
                throw err("invalid number '" + raw + "'");
            }
        }

        private boolean peek(char expected) {

            return i < s.length()
                    && s.charAt(i) == expected;
        }

        private boolean isDigitOneToNine(char c) {

            return c >= '1' && c <= '9';
        }
    }

    // ================================================================
    // WRITE
    // ================================================================

    /**
     * Writes a compact JSON representation.
     */
    public static String write(Object value) {

        StringBuilder builder =
                new StringBuilder();

        write(
                value,
                builder,
                0,
                false
        );

        return builder.toString();
    }

    /**
     * Writes a human-readable JSON representation.
     */
    public static String writePretty(Object value) {

        StringBuilder builder =
                new StringBuilder();

        write(
                value,
                builder,
                0,
                true
        );

        return builder.toString();
    }

    /**
     * Serializes supported Java values to JSON.
     */
    private static void write(
            Object value,
            StringBuilder builder,
            int depth,
            boolean pretty) {

        if (value == null) {

            builder.append("null");
            return;
        }

        if (value instanceof String string) {

            escape(string, builder);
            return;
        }

        if (value instanceof Character character) {

            escape(
                    String.valueOf(character),
                    builder
            );

            return;
        }

        if (value instanceof Boolean booleanValue) {

            builder.append(booleanValue);
            return;
        }

        /*
         * BigDecimal is particularly important for this assignment.
         *
         * Do not convert it to double.
         */
        if (value instanceof BigDecimal decimal) {

            builder.append(decimal.toPlainString());
            return;
        }

        if (value instanceof Number number) {

            builder.append(number);
            return;
        }

        if (value instanceof Map<?, ?> map) {

            writeObject(
                    map,
                    builder,
                    depth,
                    pretty
            );

            return;
        }

        if (value instanceof Iterable<?> iterable) {

            writeArray(
                    iterable,
                    builder,
                    depth,
                    pretty
            );

            return;
        }

        /*
         * The rest of the project may pass enums or other simple values.
         * Represent them as JSON strings rather than emitting invalid JSON.
         */
        escape(
                String.valueOf(value),
                builder
        );
    }

    // ================================================================
    // WRITE OBJECT
    // ================================================================

    private static void writeObject(
            Map<?, ?> map,
            StringBuilder builder,
            int depth,
            boolean pretty) {

        if (map.isEmpty()) {

            builder.append("{}");
            return;
        }

        builder.append('{');

        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {

            if (!first) {
                builder.append(',');
            }

            first = false;

            newline(
                    builder,
                    depth + 1,
                    pretty
            );

            escape(
                    String.valueOf(entry.getKey()),
                    builder
            );

            builder.append(':');

            if (pretty) {
                builder.append(' ');
            }

            write(
                    entry.getValue(),
                    builder,
                    depth + 1,
                    pretty
            );
        }

        newline(
                builder,
                depth,
                pretty
        );

        builder.append('}');
    }

    // ================================================================
    // WRITE ARRAY
    // ================================================================

    private static void writeArray(
            Iterable<?> iterable,
            StringBuilder builder,
            int depth,
            boolean pretty) {

        builder.append('[');

        boolean first = true;

        for (Object value : iterable) {

            if (!first) {
                builder.append(',');
            }

            first = false;

            newline(
                    builder,
                    depth + 1,
                    pretty
            );

            write(
                    value,
                    builder,
                    depth + 1,
                    pretty
            );
        }

        if (!first) {
            newline(
                    builder,
                    depth,
                    pretty
            );
        }

        builder.append(']');
    }

    // ================================================================
    // PRETTY PRINT SUPPORT
    // ================================================================

    private static void newline(
            StringBuilder builder,
            int depth,
            boolean pretty) {

        if (!pretty) {
            return;
        }

        builder.append('\n');

        builder.append(
                "  ".repeat(depth)
        );
    }

    // ================================================================
    // STRING ESCAPING
    // ================================================================

    private static void escape(
            String value,
            StringBuilder builder) {

        builder.append('"');

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            switch (c) {

                case '"' -> builder.append("\\\"");

                case '\\' -> builder.append("\\\\");

                case '\b' -> builder.append("\\b");

                case '\f' -> builder.append("\\f");

                case '\n' -> builder.append("\\n");

                case '\r' -> builder.append("\\r");

                case '\t' -> builder.append("\\t");

                default -> {

                    if (c < 0x20) {

                        builder.append(
                                String.format(
                                        "\\u%04x",
                                        (int) c
                                )
                        );

                    } else {

                        builder.append(c);
                    }
                }
            }
        }

        builder.append('"');
    }
}