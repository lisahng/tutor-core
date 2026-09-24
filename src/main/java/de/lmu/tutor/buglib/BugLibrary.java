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

    /**
     * Vorlaeufiges beta fuer Kategorien, fuer die noch kein Wert geschaetzt wurde.
     * Neutral, das heisst ohne Vorannahme ueber die Schwierigkeit: eine Kategorie
     * startet damit bei einer Erfolgswahrscheinlichkeit von 0,5.
     */
    public static final double BETA_PLATZHALTER = 0.0;

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
                leseBeta(o),
                str(o, "konzept"),
                List.copyOf(feedback),
                List.copyOf(untertypen));
    }

    private static String str(Map<String, Object> o, String key) {
        Object v = o.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Liefert die PFA-Leichtigkeit beta einer Kategorie.
     *
     * <p>Steht in der JSON ein Feld {@code beta}, wird es direkt uebernommen. Fehlt es,
     * gilt {@link #BETA_PLATZHALTER} fuer alle Kategorien gleichermassen.</p>
     *
     * <p>Der Platzhalter ist bewusst neutral gewaehlt und wird ausdruecklich <em>nicht</em>
     * aus dem Feld {@code basisgewicht} abgeleitet. Eine solche Umrechnung waere frei
     * erfunden: {@code basisgewicht} ist eine grobe Haeufigkeitseinschaetzung, beta dagegen
     * ein aus Loesungsquoten geschaetzter Modellparameter. Aus der einen Groesse die andere
     * zu rechnen, wuerde eine Kalibrierung vortaeuschen, die es nicht gibt.</p>
     *
     * <p>Solange beta fehlt, startet jede Kategorie bei einer Erfolgswahrscheinlichkeit von
     * 0,5. Die Aufgabenauswahl richtet sich dann allein nach der Fehlerhistorie der Person,
     * nicht nach angenommenen Schwierigkeitsunterschieden. Das ist die zurueckhaltendere
     * Annahme. Sobald aus der Studie Loesungsquoten je Kategorie vorliegen, wird beta daraus
     * geschaetzt und als Feld {@code beta} in die JSON geschrieben; dieser Zweig greift
     * dann nicht mehr.</p>
     */
    private static double leseBeta(Map<String, Object> o) {
        Object explizit = o.get("beta");
        if (explizit instanceof Number n) {
            return n.doubleValue();
        }
        return BETA_PLATZHALTER;
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