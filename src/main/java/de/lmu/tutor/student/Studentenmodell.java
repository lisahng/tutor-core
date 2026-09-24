package de.lmu.tutor.student;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;

/**
 * Das Studentenmodell (Tabelle 4 der Zulassungsarbeit): "Speichert Fehlerhistorie und
 * Schwierigkeitsgrad pro Lernender fuer adaptive Aufgabengenerierung".
 *
 * <p>Ein Modell gehoert zu genau einer Person. Es haelt je Bug-Kategorie zwei Zaehler,
 * Erfolge und Fehler, und leitet daraus zwei Dinge ab:</p>
 *
 * <ol>
 *   <li><b>Die Scaffolding-Stufe.</b> {@link #wiederholungen(String)} liefert, wie oft
 *       diese Person in dieser Kategorie schon falsch lag. Genau diese Zahl erwartet der
 *       Feedbackgenerator aus Schritt 6, um zwischen allgemeinem Hinweis, Regel und
 *       Loesungsschritt zu waehlen.</li>
 *   <li><b>Die naechste Aufgabe.</b> {@link #naechsteKategorie()} waehlt ueber die
 *       Performance Factors Analysis die Kategorie mit der niedrigsten geschaetzten
 *       Erfolgswahrscheinlichkeit, uebt also gezielt die Schwaechen.</li>
 * </ol>
 *
 * <p><b>Wiederholungssperre.</b> Ohne Gegenmassnahme wuerde PFA dieselbe Kategorie
 * mehrfach hintereinander waehlen, denn ein Fehler senkt ihre Wahrscheinlichkeit weiter.
 * Das Modell merkt sich deshalb die zuletzt gestellten Kategorien und ueberspringt sie,
 * solange es Alternativen gibt. Sind alle uebrigen Kategorien gesperrt, faellt die Sperre
 * weg, damit immer eine Aufgabe zustande kommt.</p>
 *
 * <p><b>Speicherung.</b> Diese Fassung haelt alles im Arbeitsspeicher, die Daten leben
 * also nur so lange wie das Objekt. Fuer den Prototyp und die Sitzungen der Studie
 * reicht das. Eine spaetere Ablage in H2 kann dieselbe Schnittstelle bedienen, weil nach
 * aussen nur {@code erfasseErgebnis}, {@code wiederholungen} und {@code naechsteKategorie}
 * sichtbar sind.</p>
 *
 * <p><b>Reihenfolge im Ablauf.</b> {@link #wiederholungen(String)} muss vor
 * {@link #erfasseErgebnis(String, boolean)} abgefragt werden. Sonst zaehlt der gerade
 * gemachte Fehler bereits mit und die Person ueberspringt beim ersten Fehler die Stufe 0.
 * {@link #erfasseUndGibWiederholungen(String, boolean)} nimmt einem diese Reihenfolge ab.</p>
 */
public final class Studentenmodell {

    /** Zaehlerstaende je Kategorie: Index 0 = Erfolge, Index 1 = Fehler. */
    private final Map<String, int[]> statistik = new LinkedHashMap<>();

    /** Die zuletzt gestellten Kategorien, juengste zuerst. Laenge begrenzt durch sperreLaenge. */
    private final Deque<String> zuletztGestellt = new ArrayDeque<>();

    private final BugLibrary bibliothek;
    private final PfaParameter parameter;
    private final int sperreLaenge;

    /** Standardmodell: PFA-Standardparameter, keine direkte Wiederholung derselben Kategorie. */
    public Studentenmodell(BugLibrary bibliothek) {
        this(bibliothek, PfaParameter.STANDARD, 1);
    }

    /**
     * @param bibliothek   die Bug Library, liefert beta je Kategorie
     * @param parameter    die PFA-Lernraten
     * @param sperreLaenge wie viele zuletzt gestellte Kategorien gesperrt bleiben
     *                     (0 schaltet die Sperre ab)
     */
    public Studentenmodell(BugLibrary bibliothek, PfaParameter parameter, int sperreLaenge) {
        if (sperreLaenge < 0) {
            throw new IllegalArgumentException("sperreLaenge darf nicht negativ sein");
        }
        this.bibliothek = bibliothek;
        this.parameter = parameter;
        this.sperreLaenge = sperreLaenge;
    }

    // ---------------------------------------------------------------
    // Erfassen
    // ---------------------------------------------------------------

    /** Verbucht das Ergebnis eines Loesungsversuchs in der genannten Kategorie. */
    public void erfasseErgebnis(String kategorieId, boolean korrekt) {
        int[] zaehler = statistik.computeIfAbsent(kategorieId, id -> new int[]{0, 0});
        zaehler[korrekt ? 0 : 1]++;
    }

    /**
     * Verbucht das Ergebnis und liefert die Wiederholungszahl <em>vor</em> diesem Versuch.
     * Das ist der bequeme Weg, weil der zurueckgegebene Wert direkt an den
     * Feedbackgenerator weitergereicht werden kann.
     */
    public int erfasseUndGibWiederholungen(String kategorieId, boolean korrekt) {
        int vorher = wiederholungen(kategorieId);
        erfasseErgebnis(kategorieId, korrekt);
        return vorher;
    }

    /** Setzt alle Zaehler und die Sperre zurueck, etwa zwischen zwei Sitzungen. */
    public void zuruecksetzen() {
        statistik.clear();
        zuletztGestellt.clear();
    }

    // ---------------------------------------------------------------
    // Abfragen
    // ---------------------------------------------------------------

    /**
     * Wie oft diese Person in dieser Kategorie bisher falsch lag. Steuert die
     * Scaffolding-Stufe des Feedbackgenerators (0 = erster Fehler).
     */
    public int wiederholungen(String kategorieId) {
        return fehler(kategorieId);
    }

    public int erfolge(String kategorieId) {
        return statistik.getOrDefault(kategorieId, new int[]{0, 0})[0];
    }

    public int fehler(String kategorieId) {
        return statistik.getOrDefault(kategorieId, new int[]{0, 0})[1];
    }

    /** Gesamtzahl aller Versuche ueber alle Kategorien. */
    public int versucheGesamt() {
        int summe = 0;
        for (int[] z : statistik.values()) {
            summe += z[0] + z[1];
        }
        return summe;
    }

    /** Die PFA-Schaetzung fuer die naechste Aufgabe dieser Kategorie. */
    public double erfolgswahrscheinlichkeit(String kategorieId) {
        return bibliothek.erfolgswahrscheinlichkeit(
                kategorieId, erfolge(kategorieId), fehler(kategorieId),
                parameter.gamma(), parameter.rho());
    }

    /** Momentaufnahme einer Kategorie. */
    public Kategoriestatistik statistik(String kategorieId) {
        return new Kategoriestatistik(kategorieId, erfolge(kategorieId), fehler(kategorieId),
                erfolgswahrscheinlichkeit(kategorieId));
    }

    /**
     * Momentaufnahme aller Kategorien der Bug Library, auch der noch nicht geuebten.
     * Fuer das Systemprotokoll der Studie gedacht.
     */
    public List<Kategoriestatistik> alleStatistiken() {
        List<Kategoriestatistik> ergebnis = new ArrayList<>();
        for (Misconception m : bibliothek.all()) {
            ergebnis.add(statistik(m.id()));
        }
        return List.copyOf(ergebnis);
    }

    /** Die aktuell gesperrten Kategorien, juengste zuerst. */
    public List<String> gesperrteKategorien() {
        return List.copyOf(zuletztGestellt);
    }

    // ---------------------------------------------------------------
    // Aufgabenauswahl
    // ---------------------------------------------------------------

    /**
     * Waehlt die naechste zu uebende Kategorie: die mit der niedrigsten geschaetzten
     * Erfolgswahrscheinlichkeit, unter Auslassung der zuletzt gestellten.
     *
     * <p>Die gewaehlte Kategorie wandert in die Sperre. Bei Gleichstand gewinnt die
     * Kategorie, die in der Bug Library zuerst steht, damit die Auswahl reproduzierbar
     * bleibt.</p>
     *
     * @return die Kategorie, oder ein leeres Optional bei leerer Bug Library
     */
    public Optional<Misconception> naechsteKategorie() {
        Optional<Misconception> gewaehlt = waehleAus(true);
        if (gewaehlt.isEmpty()) {
            // Alle Kategorien gesperrt: Sperre fuer diesen Zug ignorieren.
            gewaehlt = waehleAus(false);
        }
        gewaehlt.ifPresent(m -> vermerkeGestellt(m.id()));
        return gewaehlt;
    }

    private Optional<Misconception> waehleAus(boolean sperreBeachten) {
        Misconception beste = null;
        double kleinsteWahrscheinlichkeit = Double.MAX_VALUE;
        for (Misconception m : bibliothek.all()) {
            if (sperreBeachten && zuletztGestellt.contains(m.id())) {
                continue;
            }
            double p = erfolgswahrscheinlichkeit(m.id());
            if (p < kleinsteWahrscheinlichkeit) {
                kleinsteWahrscheinlichkeit = p;
                beste = m;
            }
        }
        return Optional.ofNullable(beste);
    }

    private void vermerkeGestellt(String kategorieId) {
        if (sperreLaenge == 0) {
            return;
        }
        zuletztGestellt.remove(kategorieId);
        zuletztGestellt.addFirst(kategorieId);
        while (zuletztGestellt.size() > sperreLaenge) {
            zuletztGestellt.removeLast();
        }
    }
}