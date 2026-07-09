package de.lmu.tutor.buglib;

import de.lmu.tutor.buglib.json.MiniJson;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry und Loader der Bug Library.
 *
 * <p>Die Bug Library ist die zentrale Datengrundlage des Tutorsystems: der
 * empirisch fundierte Katalog der 14 Fehlerkategorien (B01-B14) aus FQ1.
 * Diese Klasse laedt die Kategorien aus {@code bug-library.json}, stellt
 * Abfragen bereit und berechnet das Auswahlgewicht einer Kategorie.</p>
 */
public final class BugLibrary {

    /** Standard-Ressourcenpfad der Bug-Library-Daten (im Classpath). */
    public static final String RESOURCE = "/bug-library.json";

    private final List<Misconception> eintraege;
    private final Map<String, Misconception> nachId;

    private BugLibrary(List<Misconception> eintraege) {
        this.eintraege = List.copyOf(eintraege);
        Map<String, Misconception> map = new LinkedHashMap<>();
        for (Misconception m : eintraege) {
            map.put(m.id(), m);
        }
        this.nachId = Collections.unmodifiableMap(map);
    }

    // ---------------------------------------------------------------
    // Laden
    // ---------------------------------------------------------------

    /** Laedt die Bug Library aus der Standard-Ressource im Classpath. */
    public static BugLibrary loadDefault() {
        try (InputStream in = BugLibrary.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource nicht gefunden: " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Bug Library nicht laden", e);
        }
    }

    /** Laedt die Bug Library aus einer Datei (z. B. fuer Tests oder alternative Datenstaende). */
    public static BugLibrary load(Path datei) {
        try {
            return parse(Files.readString(datei, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Bug Library nicht laden: " + datei, e);
        }
    }

    /** Baut die Bug Library aus einem JSON-Text auf. */
    @SuppressWarnings("unchecked")
    public static BugLibrary parse(String json) {
        Object wurzel = MiniJson.parse(json);
        if (!(wurzel instanceof Map)) {
            throw new IllegalArgumentException("Erwartet ein JSON-Objekt an der Wurzel");
        }
        Map<String, Object> obj = (Map<String, Object>) wurzel;
        Object liste = obj.get("misconceptions");
        if (!(liste instanceof List)) {
            throw new IllegalArgumentException("Feld 'misconceptions' fehlt oder ist kein Array");
        }

        List<Misconception> ergebnis = new ArrayList<>();
        for (Object element : (List<Object>) liste) {
            ergebnis.add(leseMisconception((Map<String, Object>) element));
        }
        return new BugLibrary(ergebnis);
    }

    @SuppressWarnings("unchecked")
    private static Misconception leseMisconception(Map<String, Object> o) {
        List<String> feedback = new ArrayList<>();
        Object fb = o.get("feedback");
        if (fb instanceof List) {
            for (Object stufe : (List<Object>) fb) {
                feedback.add(String.valueOf(stufe));
            }
        }

        List<Subtype> untertypen = new ArrayList<>();
        Object ut = o.get("untertypen");
        if (ut instanceof List) {
            for (Object sub : (List<Object>) ut) {
                Map<String, Object> s = (Map<String, Object>) sub;
                untertypen.add(new Subtype(
                        str(s, "id"),
                        str(s, "name"),
                        str(s, "beispiel")));
            }
        }

        return new Misconception(
                str(o, "id"),
                str(o, "name"),
                str(o, "beschreibung"),
                str(o, "beispiel"),
                str(o, "typischerFehler"),
                Difficulty.vonText(str(o, "schwierigkeit")),
                (int) Math.round(num(o, "basisgewicht")),
                str(o, "konzept"),
                List.copyOf(feedback),
                List.copyOf(untertypen));
    }

    private static String str(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static double num(Map<String, Object> o, String key) {
        Object v = o.get(key);
        if (v instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("Zahl erwartet fuer Feld '" + key + "'");
    }

    // ---------------------------------------------------------------
    // Abfragen
    // ---------------------------------------------------------------

    /** Alle Kategorien in Reihenfolge der Datei (B01..B14). */
    public List<Misconception> all() {
        return eintraege;
    }

    /** Anzahl der Kategorien. */
    public int size() {
        return eintraege.size();
    }

    /** Eine Kategorie ueber ihre ID (z. B. "B05"), falls vorhanden. */
    public Optional<Misconception> byId(String id) {
        return Optional.ofNullable(nachId.get(id));
    }

    /** Alle Kategorien eines Schwierigkeitsgrads. */
    public List<Misconception> byDifficulty(Difficulty grad) {
        List<Misconception> res = new ArrayList<>();
        for (Misconception m : eintraege) {
            if (m.schwierigkeit() == grad) res.add(m);
        }
        return res;
    }

    // ---------------------------------------------------------------
    // Aufgabenauswahl / Gewichtung
    // ---------------------------------------------------------------

    /**
     * Auswahlgewicht einer Kategorie nach der Formel
     * <pre>  w(K, s) = b(K) * (1 + alpha * f(K, s))  </pre>
     * mit dem Basisgewicht b(K) aus der Bug Library, der individuellen
     * Fehlerhaeufigkeit f(K, s) (0..1) und der Adaptionsstaerke alpha.
     *
     * @param id          Kategorie-ID
     * @param fehlerquote f(K, s), relative Fehlerhaeufigkeit der Person (0..1)
     * @param alpha       Adaptionsstaerke (>= 0)
     * @return das Auswahlgewicht; 0, falls die Kategorie unbekannt ist
     */
    public double auswahlGewicht(String id, double fehlerquote, double alpha) {
        return byId(id)
                .map(m -> m.basisgewicht() * (1.0 + alpha * fehlerquote))
                .orElse(0.0);
    }
}
