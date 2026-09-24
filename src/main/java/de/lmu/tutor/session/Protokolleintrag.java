package de.lmu.tutor.session;

import java.time.Instant;

import de.lmu.tutor.diagnose.Diagnose;

/**
 * Ein Eintrag im Systemprotokoll: was bei einem Loesungsversuch passiert ist.
 *
 * <p>Die Felder entsprechen den in Abschnitt 6.3.4 der Zulassungsarbeit genannten
 * Daten: Fehlerhaeufigkeit je Bug-Kategorie, Bearbeitungszeit je Aufgabe, Anzahl der
 * Versuche, abgerufene Feedback-Stufen sowie die gestellte Diagnose je Fehleingabe.
 * Pro Antwort entsteht genau ein Eintrag, auch bei mehreren Versuchen an derselben
 * Aufgabe.</p>
 *
 * <p>Die Diagnose wird bewusst mitsamt ihrer Konfidenz festgehalten. Fuer den Abgleich
 * mit den Think-Aloud-Protokollen ist der Unterschied wesentlich: Eine mit
 * {@code SIMULIERT} begruendete Diagnose beruht auf einem reproduzierten Fehlwert,
 * eine mit {@code ZIELKATEGORIE} nur auf der Herkunft der Aufgabe. Beide als gleich
 * sichere Treffer zu zaehlen, wuerde die Trefferquote schoenrechnen.</p>
 *
 * @param zeitpunkt     Zeitpunkt der Antwort
 * @param kategorieId   die Bug-Kategorie, fuer die die Aufgabe erzeugt wurde
 * @param ausdruck      der gestellte Ausdruck als Java-Quelltext
 * @param belegung      die Variablenbelegung, leer wenn die Aufgabe keine Variablen hat
 * @param referenz      die korrekte Loesung als Text
 * @param antwort       die Eingabe der Person als Text
 * @param versuch       der wievielte Versuch an dieser Aufgabe (beginnend bei 1)
 * @param korrekt       ob die Antwort richtig war
 * @param diagnoseId    die diagnostizierte Bug-Kategorie, leer wenn keine erkannt wurde
 * @param konfidenz     wie sicher die Diagnose ist
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
        String diagnoseId,
        Diagnose.Konfidenz konfidenz,
        int feedbackStufe,
        long dauerMillis
) {

    /** Kopfzeile fuer den CSV-Export, passend zu {@link #alsCsvZeile()}. */
    public static String csvKopfzeile() {
        return "zeitpunkt;kategorie;ausdruck;belegung;referenz;antwort;versuch;"
                + "korrekt;diagnose;konfidenz;feedbackStufe;dauerMillis";
    }

    /**
     * Der Eintrag als CSV-Zeile mit Semikolon als Trennzeichen.
     *
     * <p>Semikolon deshalb, weil die ausgegebenen Ausdruecke Kommata enthalten koennen
     * (etwa in Array-Belegungen) und weil Excel im deutschen Gebietsschema
     * Semikolon erwartet. Felder werden zusaetzlich in Anfuehrungszeichen gesetzt, damit
     * ein Semikolon im Ausdruck die Spalten nicht verschiebt.</p>
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
                zitiere(diagnoseId),
                zitiere(konfidenz.name()),
                String.valueOf(feedbackStufe),
                String.valueOf(dauerMillis));
    }

    /** Setzt ein Feld in Anfuehrungszeichen und verdoppelt darin enthaltene Anfuehrungszeichen. */
    private static String zitiere(String feld) {
        return "\"" + feld.replace("\"", "\"\"") + "\"";
    }
}