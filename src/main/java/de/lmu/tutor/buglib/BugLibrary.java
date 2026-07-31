package de.lmu.tutor.buglib;

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

import de.lmu.tutor.buglib.json.MiniJson;

/**
 * Registry und Loader der Bug Library (Katalog der 14 Fehlerkategorien B01-B14).
 * Laedt aus {@code bug-library.json}, stellt Abfragen bereit und berechnet die
 * Aufgabenauswahl nach Performance Factors Analysis (PFA, Pavlik et al. 2009).
 */
public final class BugLibrary {

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

    public static BugLibrary load(Path datei) {
        try {
            return parse(Files.readString(datei, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Bug Library nicht laden: " + datei, e);
        }
    }

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
                untertypen.add(new Subtype(str(s, "id"), str(s, "name"), str(s, "beispiel")));
            }
        }

        // Hinweis: ein evtl. noch vorhandenes Feld "schwierigkeit" in der JSON wird
        // ignoriert - die Schwierigkeit wird jetzt aus beta abgeleitet.
        return new Misconception(
                str(o, "id"),
                str(o, "name"),
                str(o, "beschreibung"),
                str(o, "beispiel"),
                str(o, "typischerFehler"),
                numOrDefault(o, "beta", 0.0),
                str(o, "konzept"),
                List.copyOf(feedback),
                List.copyOf(untertypen));
    }

    private static String str(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /** Liest ein Zahlenfeld; fehlt es oder ist es keine Zahl, wird der Standardwert genutzt. */
    private static double numOrDefault(Map<String, Object> o, String key, double standard) {
        Object v = o.get(key);
        return (v instanceof Number n) ? n.doubleValue() : standard;
    }

    // ---------------------------------------------------------------
    // Abfragen
    // ---------------------------------------------------------------

    public List<Misconception> all() {
        return eintraege;
    }

    public int size() {
        return eintraege.size();
    }

    public Optional<Misconception> byId(String id) {
        return Optional.ofNullable(nachId.get(id));
    }

    // ---------------------------------------------------------------
    // Aufgabenauswahl nach Performance Factors Analysis (PFA)
    //   m = beta + gamma*erfolge + rho*fehler ; P(richtig) = 1/(1+e^-m)
    // Ausgewaehlt wird die am wenigsten beherrschte Kategorie (kleinste P).
    // ---------------------------------------------------------------

    public double pfaWert(String id, int erfolge, int fehler, double gamma, double rho) {
        double beta = byId(id)
                .map(Misconception::beta)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Kategorie: " + id));
        return beta + gamma * erfolge + rho * fehler;
    }

    public double erfolgswahrscheinlichkeit(String id, int erfolge, int fehler, double gamma, double rho) {
        double m = pfaWert(id, erfolge, fehler, gamma, rho);
        return 1.0 / (1.0 + Math.exp(-m));
    }

    public Optional<Misconception> naechsteKategorie(Map<String, int[]> statistik, double gamma, double rho) {
        Misconception beste = null;
        double minP = Double.MAX_VALUE;
        for (Misconception m : eintraege) {
            int[] sf = statistik.getOrDefault(m.id(), new int[]{0, 0});
            double p = erfolgswahrscheinlichkeit(m.id(), sf[0], sf[1], gamma, rho);
            if (p < minP) {
                minP = p;
                beste = m;
            }
        }
        return Optional.ofNullable(beste);
    }
}