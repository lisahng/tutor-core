package de.lmu.tutor.web;

import java.time.Instant;

import de.lmu.tutor.session.Teilnehmer;

/**
 * Wie hilfreich eine Person die Rueckmeldung zu einer Aufgabe fand.
 *
 * <p>Erhoben wird einmal je Aufgabe, nicht je Versuch, und nur dann, wenn es ueberhaupt
 * eine Rueckmeldung auf einen Fehler gab. Wer auf Anhieb richtig liegt, bekommt nur Lob,
 * und ein Lob auf seine Hilfestellung hin zu bewerten ergibt keinen Sinn.</p>
 *
 * <p><b>Warum eine Bewertung je Aufgabe und nicht nur einmal am Ende.</b> Der Fragebogen
 * am Schluss misst einen Gesamteindruck, und der ist von der letzten Aufgabe und vom
 * Gesamtergebnis gefaerbt. Hier entstehen dagegen etwa zwanzig Messpunkte je Person,
 * jeweils unmittelbar nach dem Lesen der Rueckmeldung. Narciss (2008, 2013) behandelt die
 * wahrgenommene Nuetzlichkeit von Feedback als eigene Groesse neben seiner tatsaechlichen
 * Wirkung, und genau die wird hier erfasst.</p>
 *
 * <p><b>Was sich damit auswerten laesst.</b> Drei Dinge, und das dritte ist das
 * eigentliche Argument fuer diese Erhebung:</p>
 * <ol>
 *   <li>Zwischen den Gruppen, also ob diagnostisches Feedback als hilfreicher erlebt wird
 *       als die blosse Auskunft richtig oder falsch. Das ist die feinkoernige Entsprechung
 *       zu Item P5 bei Schmutz, Blanchette und Strickroth (2026).</li>
 *   <li>Ueber die Scaffolding-Stufen hinweg, also ob die konkreteren Stufen hilfreicher
 *       erlebt werden als die allgemeinen.</li>
 *   <li><b>Innerhalb der Tutorgruppe zwischen erklaerten und unerklaerten Diagnosen.</b>
 *       Wird eine begruendete Diagnose hoeher bewertet als der Rueckfall auf Model
 *       Tracing, ist das ein unabhaengiger Beleg dafuer, dass die Bug Library trifft. Das
 *       stuetzt Strang 1 und 2 mit Daten, die nicht aus dem System selbst stammen, sondern
 *       von den Lernenden.</li>
 * </ol>
 *
 * <p>Die Punkte drei bis fuenf dieser Auswertung sind statistisch belastbar, weil sie auf
 * mehreren hundert Beobachtungen beruhen und nicht auf zwanzig Personen.</p>
 *
 * @param zeitpunkt       Zeitpunkt der Bewertung
 * @param teilnehmerId    Pseudonym der Person
 * @param gruppe          die Versuchsbedingung
 * @param aufgabenNummer  die wievielte Aufgabe der Sitzung
 * @param kategorieId     die Kategorie, fuer die die Aufgabe erzeugt wurde
 * @param ausdruck        der gestellte Ausdruck
 * @param versuche        wie viele Versuche diese Aufgabe gekostet hat
 * @param korrekt         ob sie am Ende geloest wurde
 * @param feedbackStufe   die zuletzt gezeigte Scaffolding-Stufe
 * @param diagnose        die gestellte Diagnose, leer wenn keine begruendet werden konnte
 * @param erklaert        ob die Diagnose begruendet war
 * @param bewertung       1 bis 5, oder 0 wenn uebersprungen
 */
public record Feedbackbewertung(
        Instant zeitpunkt,
        String teilnehmerId,
        Teilnehmer.Gruppe gruppe,
        int aufgabenNummer,
        String kategorieId,
        String ausdruck,
        int versuche,
        boolean korrekt,
        int feedbackStufe,
        String diagnose,
        boolean erklaert,
        int bewertung
) {

    /** Die Skala, von gar nicht hilfreich bis sehr hilfreich. */
    public static final int KLEINSTE = 1;
    public static final int GROESSTE = 5;

    /** Wert fuer eine uebersprungene Bewertung. */
    public static final int UEBERSPRUNGEN = 0;

    public boolean abgegeben() {
        return bewertung >= KLEINSTE && bewertung <= GROESSTE;
    }

    public static String csvKopfzeile() {
        return "zeitpunkt;teilnehmer;gruppe;aufgabenNummer;kategorie;ausdruck;versuche;"
                + "korrekt;feedbackStufe;diagnose;erklaert;bewertung";
    }

    /** Dieselbe Schreibweise wie im Systemprotokoll, Semikolon und Anfuehrungszeichen. */
    public String alsCsvZeile() {
        return String.join(";",
                zitiere(zeitpunkt.toString()),
                zitiere(teilnehmerId),
                zitiere(gruppe.name()),
                String.valueOf(aufgabenNummer),
                zitiere(kategorieId),
                zitiere(ausdruck),
                String.valueOf(versuche),
                String.valueOf(korrekt),
                String.valueOf(feedbackStufe),
                zitiere(diagnose),
                String.valueOf(erklaert),
                String.valueOf(bewertung));
    }

    private static String zitiere(String feld) {
        String sicher = feld == null ? "" : feld;
        return "\"" + sicher.replace("\"", "\"\"") + "\"";
    }
}
