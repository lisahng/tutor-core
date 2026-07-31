package de.lmu.tutor.buglib.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein kleiner, abhaengigkeitsfreier JSON-Parser (rekursiver Abstieg).
 *
 * <p>Er wird nur genutzt, damit die Bug Library ohne externe Bibliothek geladen
 * werden kann und das Projekt sofort mit reinem JDK laeuft. In einer vollen
 * Maven-Umgebung kann der Loader spaeter problemlos auf Jackson umgestellt
 * werden (siehe README) - dieser Parser ist dann nicht mehr noetig.</p>
 *
 * <p>Rueckgabetypen: {@link Map} (Objekt), {@link List} (Array),
 * {@link String}, {@link Double} (Zahl), {@link Boolean} und {@code null}.</p>
 */
public final class MiniJson {

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parst einen JSON-Text und liefert den Wurzelwert. */
    public static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos < p.text.length()) {
            throw new JsonException("Unerwartete Zeichen nach dem JSON-Wert an Position " + p.pos);
        }
        return value;
    }

    private Object readValue() {
        char c = peek();
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': case 'f': return readBoolean();
            case 'n': return readNull();
            default:  return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') break;
            if (c != ',') throw new JsonException("Erwartet ',' oder '}' an Position " + (pos - 1));
        }
        return map;
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') break;
            if (c != ',') throw new JsonException("Erwartet ',' oder ']' an Position " + (pos - 1));
        }
        return list;
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') break;
            if (c == '\\') {
                char esc = next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = text.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> throw new JsonException("Ungueltige Escape-Sequenz \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean readBoolean() {
        if (text.startsWith("true", pos))  { pos += 4; return Boolean.TRUE; }
        if (text.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new JsonException("Ungueltiger Wahrheitswert an Position " + pos);
    }

    private Object readNull() {
        if (text.startsWith("null", pos)) { pos += 4; return null; }
        throw new JsonException("Ungueltiges Literal an Position " + pos);
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new JsonException("Kein gueltiger Wert an Position " + pos);
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    // ---- Hilfsmethoden ----

    private char peek() {
        if (pos >= text.length()) throw new JsonException("Unerwartetes Ende des JSON-Textes");
        return text.charAt(pos);
    }

    private char next() {
        if (pos >= text.length()) throw new JsonException("Unerwartetes Ende des JSON-Textes");
        return text.charAt(pos++);
    }

    private void expect(char c) {
        char actual = next();
        if (actual != c) {
            throw new JsonException("Erwartet '" + c + "', gefunden '" + actual + "' an Position " + (pos - 1));
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    /** Fehler beim Parsen von JSON. */
    public static class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }
}
