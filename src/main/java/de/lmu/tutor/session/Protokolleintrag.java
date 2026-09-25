package de.lmu.tutor.session;

import java.time.Instant;

import de.lmu.tutor.diagnose.Diagnose;

/**
 * Ein Eintrag im Systemprotokoll: was bei einem Loesungsversuch passiert ist.
 *
 * <p>Die Felder entsprechen den in Abschnitt 6.3.4 der Zulassungsarbeit genannten Daten:
 * Fehlerhaeufigkeit je Bug-Kategorie, Bearbeitungszeit je Aufgabe, Anzahl der Versuche,
 * abgerufene Feedback-Stufen sowie die gestellte Diagnose je Fehleingabe. Pro Antwort
 * entsteht genau ein Eintrag, auch bei mehreren Versuchen an derselben Aufgabe.</p>
 *
 * <p><b>Warum Diagnose und Vermutung getrennt sind.</b> Erklaert keine Fehlregel den
 * eingegebenen Wert, faellt das System auf die Kategorie zurueck, fuer die die Aufgabe
 * erzeugt wurde. Das ist eine Vermutung aus der Herkunft der Aufgabe, keine Erklaerung.
 * Wuerde sie in derselben Spalte stehen, waere die Trefferquote in Strang 3 der Studie
 * geschoent: Aufgaben einer Kategorie provozieren meist Fehler eben dieser Kategorie, das
 * System traefe also oft zufaellig richtig.</p>
 *
 * <p>Deshalb enthaelt {@code diagnose} nur begruendete Diagnosen. Alles andere steht in
 * {@code vermutung} und bleibt bei der strengen Auswertung aussen vor. Die so markierten
 * Faelle sind zugleich die aufschlussreichsten fuer eine spaetere Erweiterung der Bug
 * Library, weil sie zeigen, welche Fehler noch keine Regel abbildet.</p>
 *
 * @param zeitpunkt     Zeitpunkt der Antwort
 * @param kategorieId   die Kategorie, fuer die die Aufgabe erzeugt wurde
 * @param ausdruck      der gestellte Ausdruck als Java-Quelltext
 * @param belegung      die Variablenbelegung, leer ohne Variablen
 * @param referenz      die korrekte Loesung als Text
 * @param antwort       die Eingabe der Person als Text
 * @param versuch       der wievielte Versuch an dieser Aufgabe, beginnend bei 1
 * @param korrekt       ob die Antwort richtig war
 * @param diagnose      die begruendete Diagnose, sonst leer
 * @param vermutung     die vermutete Kategorie, wenn keine Regel den Wert erklaert, sonst leer
 * @param erklaert      ob die Kategorie begruendet werden konnte
 * @param konfidenz     wie die Kategorie zustande kam
 * @param feedbackStufe die ausgegebene Scaffolding-Stufe, -1 ohne Kategorie
 * @param dauerMillis   Zeit zwischen Aufgabenstellung und dieser Antwort
 */
public record Protokolleintrag(
        Instant zeitpunkt,
        String kategorieId,
        String ausdruck,
        String belegung,
        String referenz,
        String antwort,
        int versuch,
        boolean korrekt,
        String diagnose,
        String vermutung,
        boolean erklaert,
        Diagnose.Konfidenz konfidenz,
        int feedbackStufe,
        long dauerMillis
) {

    /** Ein Fehler, den keine Fehlregel erklaeren konnte. Kandidat fuer die Bug Library. */
    public boolean unerklaert() {
        return !korrekt && !erklaert;
    }

    /** Kopfzeile fuer den CSV-Export, passend zu {@link #alsCsvZeile()}. */
    public static String csvKopfzeile() {
        return "zeitpunkt;kategorie;ausdruck;belegung;referenz;antwort;versuch;korrekt;"
                + "diagnose;vermutung;erklaert;konfidenz;feedbackStufe;dauerMillis";
    }

    /**
     * Der Eintrag als CSV-Zeile mit Semikolon als Trennzeichen.
     *
     * <p>Semikolon deshalb, weil die Ausdruecke Kommata enthalten koennen, etwa in
     * Array-Belegungen, und weil Excel im deutschen Gebietsschema Semikolon erwartet.
     * Felder stehen zusaetzlich in Anfuehrungszeichen, damit ein Semikolon im Ausdruck die
     * Spalten nicht verschiebt.</p>
     */
    public String alsCsvZeile() {
        return String.join(";",
                zitiere(zeitpunkt.toString()),
                zitiere(kategorieId),
                zitiere(ausdruck),
                zitiere(belegung),
                zitiere(referenz),
                zitiere(antwort),
                String.valueOf(versuch),
                String.valueOf(korrekt),
                zitiere(diagnose),
                zitiere(vermutung),
                String.valueOf(erklaert),
                zitiere(konfidenz.name()),
                String.valueOf(feedbackStufe),
                String.valueOf(dauerMillis));
    }

    /** Setzt ein Feld in Anfuehrungszeichen und verdoppelt darin enthaltene Anfuehrungszeichen. */
    private static String zitiere(String feld) {
        String sicher = feld == null ? "" : feld;
        return "\"" + sicher.replace("\"", "\"\"") + "\"";
    }
}