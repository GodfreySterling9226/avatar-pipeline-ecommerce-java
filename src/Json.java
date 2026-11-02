import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON decoder for the dependency-free HTTP adapter. */
final class Json {
    private final String source;
    private int offset;
    private Json(String source) { this.source = source; }

    static Object parse(String source) {
        try {
            Json parser = new Json(source);
            Object result = parser.value();
            parser.space();
            if (parser.offset != source.length()) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException e) {
            throw new InfraiException("Invalid JSON response");
        }
    }

    private void space() {
        while (offset < source.length() && " \t\r\n".indexOf(source.charAt(offset)) >= 0) offset++;
    }
    private boolean take(char c) {
        space();
        if (offset < source.length() && source.charAt(offset) == c) { offset++; return true; }
        return false;
    }
    private void require(char c) { if (!take(c)) throw new IllegalArgumentException(); }
    private Object value() {
        space();
        char c = source.charAt(offset);
        if (c == '"') return string();
        if (take('{')) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            do { String key = string(); require(':'); map.put(key, value()); } while (take(','));
            require('}'); return map;
        }
        if (take('[')) {
            List<Object> list = new ArrayList<>();
            if (take(']')) return list;
            do { list.add(value()); } while (take(','));
            require(']'); return list;
        }
        for (String literal : new String[]{"true", "false", "null"}) {
            if (source.startsWith(literal, offset)) {
                offset += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        int start = offset;
        while (offset < source.length() && "-+0123456789.eE".indexOf(source.charAt(offset)) >= 0) offset++;
        String number = source.substring(start, offset);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException();
        return new java.math.BigDecimal(number);
    }
    private String string() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (offset < source.length()) {
            char c = source.charAt(offset++);
            if (c == '"') return result.toString();
            if (c < 32) throw new IllegalArgumentException();
            if (c == '\\') {
                char escape = source.charAt(offset++);
                switch (escape) {
                    case '"', '\\', '/' -> result.append(escape);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> { result.append((char) Integer.parseInt(source.substring(offset, offset + 4), 16)); offset += 4; }
                    default -> throw new IllegalArgumentException();
                }
            } else result.append(c);
        }
        throw new IllegalArgumentException();
    }
}
