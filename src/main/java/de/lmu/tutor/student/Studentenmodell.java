package de.lmu.tutor.student;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;

/**
 * Das Studentenmodell (Tabelle 4 der Zulassungsarbeit): speichert Fehlerhistorie und
 * Schwierigkeitsgrad pro Lernender fuer die adaptive Aufgabengenerierung.
 *
 * <p>Ein Modell gehoert zu genau einer Person. Es haelt je Bug-Kategorie zwei Zaehler,
 * Erfolge und Fehler, und leitet daraus zwei Dinge ab:</p>
 *
 * <ol>
 *   <li><b>Die Fehlerhistorie.</b> {@link #wiederholungen(String)} liefert, wie oft diese
 *       Person in dieser Kategorie schon falsch lag.</li>
 *   <li><b>Die naechste Aufgabe.</b> {@link #naechsteKategorie()} waehlt ueber die
 *       Performance Factors Analysis die Kategorie mit der niedrigsten geschaetzten
 *       Erfolgswahrscheinlichkeit, uebt also gezielt die Schwaechen.</li>
 * </ol>
 *
 * <p><b>Erste Runde mit fester Reihenfolge.</b> Solange kein kategorienspezifisches beta
 * geschaetzt ist, startet jede Kategorie bei einer Erfolgswahrscheinlichkeit von 0,5. Die
 * erste Aufgabe waere damit faktisch willkuerlich, sie ergaebe sich allein aus der
 * Reihenfolge in der Bug Library. Fuer einen fairen Gruppenvergleich ist das unbrauchbar,
 * weil sich zwei Teilnehmende sonst schon in der Aufgabenfolge unterscheiden koennten.</p>
 *
 * <p>Deshalb arbeitet das Modell die {@link #standardStartreihenfolge(BugLibrary)} ab,
 * bevor PFA greift. Sie folgt der Nummerierung aus Anhang A.1 der Arbeit und damit dem
 * Weg von den Grundlagen zu den Sonderfaellen. Ab der zweiten Runde liegt eine Historie
 * vor und die Auswahl richtet sich nach ihr.</p>
 *
 * <p><b>Wiederholungssperre.</b> Ohne Gegenmassnahme wuerde PFA dieselbe Kategorie
 * mehrfach hintereinander waehlen, denn ein Fehler senkt ihre Wahrscheinlichkeit weiter.
 * Das Modell merkt sich deshalb die zuletzt gestellten Kategorien und ueberspringt sie,
 * solange es Alternativen gibt. Sind alle uebrigen gesperrt, faellt die Sperre weg, damit
 * immer eine Aufgabe zustande kommt.</p>
 *
 * <p><b>Speicherung.</b> Diese Fassung haelt alles im Arbeitsspeicher. Eine spaetere
 * Ablage in einer Datenbank kann dieselbe Schnittstelle bedienen.</p>
 *
 * <p><b>Reihenfolge im Ablauf.</b> {@link #wiederholungen(String)} muss vor
 * {@link #erfasseErgebnis(String, boolean)} abgefragt werden, sonst zaehlt der gerade
 * gemachte Fehler bereits mit.</p>
 */
public final class Studentenmodell {

    /** Zaehlerstaende je Kategorie: Index 0 = Erfolge, Index 1 = Fehler. */
    private final Map<String, int[]> statistik = new LinkedHashMap<>();

    /** Die zuletzt gestellten Kategorien, juengste zuerst. */
    private final Deque<String> zuletztGestellt = new ArrayDeque<>();

    private final BugLibrary bibliothek;
    private final PfaParameter parameter;
    private final int sperreLaenge;

    /** Die feste Reihenfolge der ersten Runde. */
    private final List<String> startreihenfolge;

    /** Wie viele Kategorien der Startreihenfolge schon gestellt wurden. */
    private int startPosition = 0;

    /** Standardmodell: PFA-Standardparameter, Sperre von 1, feste erste Runde. */
    public Studentenmodell(BugLibrary bibliothek) {
        this(bibliothek, PfaParameter.STANDARD, 1, standardStartreihenfolge(bibliothek));
    }

    public Studentenmodell(BugLibrary bibliothek, PfaParameter parameter, int sperreLaenge) {
        this(bibliothek, parameter, sperreLaenge, standardStartreihenfolge(bibliothek));
    }

    /**
     * @param bibliothek       die Bug Library, liefert beta je Kategorie
     * @param parameter        die PFA-Lernraten
     * @param sperreLaenge     wie viele zuletzt gestellte Kategorien gesperrt bleiben
     *                         (0 schaltet die Sperre ab)
     * @param startreihenfolge die Kategorien der ersten Runde, in dieser Reihenfolge;
     *                         eine leere Liste schaltet die feste Runde ab
     */
    public Studentenmodell(BugLibrary bibliothek, PfaParameter parameter, int sperreLaenge,
                           List<String> startreihenfolge) {
        if (sperreLaenge < 0) {
            throw new IllegalArgumentException("sperreLaenge darf nicht negativ sein");
        }
        this.bibliothek = bibliothek;
        this.parameter = parameter;
        this.sperreLaenge = sperreLaenge;
        this.startreihenfolge = List.copyOf(startreihenfolge);
    }

    /**
     * Alle Kategorien in der Reihenfolge der Bug Library.
     *
     * <p>Diese Reihenfolge ist nicht frei gewaehlt, sondern entspricht der Nummerierung
     * aus Anhang A.1 der Zulassungsarbeit. Sie fuehrt von den Grundlagen zu den
     * Sonderfaellen: erst Operatorpraezedenz und Ganzzahldivision, zuletzt
     * Referenzvergleich und char-Datentyp.</p>
     *
     * <p>Eine Sortierung nach einem Schwierigkeitsgrad waere hier nicht moeglich. Die
     * Bug Library fuehrt zwar ein solches Feld, der {@code Misconception}-Record liest es
     * aber nicht ein, und beta steht ueberall auf null. Es gibt also keine Angabe, nach
     * der sich sortieren liesse, ohne eine zu erfinden.</p>
     */
    public static List<String> standardStartreihenfolge(BugLibrary bibliothek) {
        return bibliothek.all().stream()
                .map(Misconception::id)
                .toList();
    }

    /**
     * Dieselben Kategorien, aber je Person in zufaelliger Reihenfolge.
     *
     * <p>In der festen Reihenfolge begegnet jeder Person zuerst die Operatorpraezedenz,
     * dann die Ganzzahldivision und so fort. Das ist fuer eine Studie eine stille
     * Konstante: Die vorderen Kategorien treffen auf die frischeste Aufmerksamkeit, die
     * hinteren auf die ermuedetste, und bei einer Arbeitsphase mit Zeitlimit erreichen
     * manche Personen die letzten Kategorien gar nicht. Beides faellt in beiden Gruppen
     * gleich aus und verzerrt den Vergleich nicht, es erhoeht aber die Streuung und macht
     * jede Aussage ueber einzelne Kategorien wertlos.</p>
     *
     * <p>Gemischt wird aus einem Startwert, der sich aus dem Pseudonym ergibt. Dieselbe
     * Person bekommt dadurch nach einem Neustart dieselbe Reihenfolge, und der Durchlauf
     * laesst sich bei der Auswertung nachbilden. Echter Zufall waere hier schlechter,
     * denn er liesse sich hinterher nicht rekonstruieren.</p>
     *
     * <p>Wichtig fuer die Beschreibung der Methode: Gemischt wird in beiden Bedingungen.
     * Der Unterschied zwischen ihnen bleibt die Anpassung an die Person, nicht die
     * Zufaelligkeit. Die Kontrollbedingung durchlaeuft eine zufaellige, aber feste
     * Reihenfolge, die nicht auf ihre Antworten reagiert.</p>
     *
     * @param bibliothek die Bug Library
     * @param seed       Startwert des Mischens, aus dem Pseudonym abgeleitet
     */
    public static List<String> gemischteStartreihenfolge(BugLibrary bibliothek, long seed) {
        List<String> ids = new ArrayList<>(standardStartreihenfolge(bibliothek));
        Collections.shuffle(ids, new Random(streue(seed)));
        return List.copyOf(ids);
    }

    /**
     * Streut benachbarte Startwerte auseinander.
     *
     * <p>Noetig, weil die Startwerte aus dem Pseudonym stammen und sich die Pseudonyme
     * P01 bis P20 nur in den letzten Zeichen unterscheiden. Ihre Startwerte liegen damit
     * dicht beieinander, und {@link Random} liefert aus benachbarten Startwerten
     * aehnliche erste Zahlen. Ohne diesen Schritt begann ein auffaellig grosser Teil der
     * Personen mit derselben Kategorie, obwohl jede fuer sich gemischt war.</p>
     *
     * <p>Verwendet wird der Abschluss von SplitMix64. Er ist umkehrbar, erhaelt also die
     * Eindeutigkeit der Startwerte, verteilt aber jedes einzelne Bit ueber das ganze
     * Wort.</p>
     */
    private static long streue(long wert) {
        long x = wert + 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    // ---------------------------------------------------------------
    // Erfassen
    // ---------------------------------------------------------------

    /** Verbucht das Ergebnis eines Loesungsversuchs in der genannten Kategorie. */
    public void erfasseErgebnis(String kategorieId, boolean korrekt) {
        int[] zaehler = statistik.computeIfAbsent(kategorieId, id -> new int[]{0, 0});
        zaehler[korrekt ? 0 : 1]++;
    }

    /** Verbucht das Ergebnis und liefert die Wiederholungszahl vor diesem Versuch. */
    public int erfasseUndGibWiederholungen(String kategorieId, boolean korrekt) {
        int vorher = wiederholungen(kategorieId);
        erfasseErgebnis(kategorieId, korrekt);
        return vorher;
    }

    /** Setzt Zaehler, Sperre und die feste Startreihenfolge zurueck. */
    public void zuruecksetzen() {
        statistik.clear();
        zuletztGestellt.clear();
        startPosition = 0;
    }

    // ---------------------------------------------------------------
    // Abfragen
    // ---------------------------------------------------------------

    /** Wie oft diese Person in dieser Kategorie bisher falsch lag. */
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

    /** Momentaufnahme aller Kategorien, auch der noch nicht geuebten. */
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

    /** Die feste Reihenfolge der ersten Runde. */
    public List<String> startreihenfolge() {
        return startreihenfolge;
    }

    /** Ob die feste erste Runde noch laeuft. */
    public boolean inErsterRunde() {
        return startPosition < startreihenfolge.size();
    }

    // ---------------------------------------------------------------
    // Aufgabenauswahl
    // ---------------------------------------------------------------

    /**
     * Waehlt die naechste zu uebende Kategorie.
     *
     * <p>In der ersten Runde ist das die naechste Kategorie der festen Startreihenfolge.
     * Danach die mit der niedrigsten geschaetzten Erfolgswahrscheinlichkeit, unter
     * Auslassung der zuletzt gestellten. Bei Gleichstand gewinnt die Kategorie, die in der
     * Bug Library zuerst steht, damit die Auswahl reproduzierbar bleibt.</p>
     *
     * @return die Kategorie, oder ein leeres Optional bei leerer Bug Library
     */
    public Optional<Misconception> naechsteKategorie() {
        Optional<Misconception> gewaehlt = ausStartreihenfolge();
        if (gewaehlt.isEmpty()) {
            gewaehlt = waehleAus(true);
        }
        if (gewaehlt.isEmpty()) {
            // Alle Kategorien gesperrt: Sperre fuer diesen Zug ignorieren.
            gewaehlt = waehleAus(false);
        }
        gewaehlt.ifPresent(m -> vermerkeGestellt(m.id()));
        return gewaehlt;
    }

    /** Die naechste Kategorie der festen Runde, sofern sie noch laeuft. */
    private Optional<Misconception> ausStartreihenfolge() {
        while (startPosition < startreihenfolge.size()) {
            String id = startreihenfolge.get(startPosition);
            startPosition++;
            Optional<Misconception> m = bibliothek.byId(id);
            if (m.isPresent()) {
                return m;
            }
            // Eine ID aus der Reihenfolge, die es nicht mehr gibt, wird uebersprungen.
        }
        return Optional.empty();
    }

    private Optional<Misconception> waehleAus(boolean sperreBeachten) {
        Misconception beste = null;
        double kleinste = Double.MAX_VALUE;
        for (Misconception m : bibliothek.all()) {
            if (sperreBeachten && zuletztGestellt.contains(m.id())) {
                continue;
            }
            double p = erfolgswahrscheinlichkeit(m.id());
            if (p < kleinste) {
                kleinste = p;
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