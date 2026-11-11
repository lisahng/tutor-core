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
 * <p><b>Wofuer die Zaehlerstaende gut sind.</b> {@code erfolgeVorher} und
 * {@code fehlerVorher} halten fest, wie die Fehlerhistorie <em>vor</em> diesem Versuch
 * aussah. Genau diese beiden Zahlen braucht die logistische Regression, mit der sich
 * gamma und rho nach der Studie aus den Logdaten schaetzen lassen. Sie nachtraeglich aus
 * der Reihenfolge der Zeilen zu rekonstruieren waere moeglich, aber fehleranfaellig.
 * {@code historieKategorie} nennt dazu die Kategorie, unter der dieser Versuch verbucht
 * wurde. Das ist nicht immer die Zielkategorie der Aufgabe, denn die Historie laeuft ueber
 * die diagnostizierte Fehlvorstellung.</p>
 *
 * <p><b>Wofuer die beiden Zeitspalten gut sind.</b> {@code dauerVersuchSekunden} misst den
 * einzelnen Versuch, {@code dauerSekunden} die Zeit seit der Aufgabenstellung. Die zweite
 * laesst sich aus der ersten aufsummieren, aber nur solange alle Zeilen einer Aufgabe
 * vorliegen und richtig sortiert sind. Beim Filtern in Excel ist das schnell nicht mehr der
 * Fall, und die Dauer eines Versuchs ist gerade die Zahl, die im Gespraech interessiert:
 * Wer nach einer Rueckmeldung sofort erneut antwortet, hat sie nicht gelesen.</p>
 *
 * @param zeitpunkt         Zeitpunkt der Antwort
 * @param teilnehmerId      Pseudonym der Person, etwa "P07"
 * @param gruppe            die Versuchsbedingung
 * @param aufgabenNummer    die wievielte Aufgabe der Sitzung, beginnend bei 1
 * @param kategorieId       die Kategorie, fuer die die Aufgabe erzeugt wurde
 * @param ausdruck          der gestellte Ausdruck als Java-Quelltext
 * @param belegung          die Variablenbelegung, leer ohne Variablen
 * @param referenz          die korrekte Loesung als Text
 * @param antwort           die Eingabe der Person als Text
 * @param versuch           der wievielte Versuch an dieser Aufgabe, beginnend bei 1
 * @param korrekt           ob die Antwort richtig war
 * @param diagnose          die begruendete Diagnose, sonst leer
 * @param vermutung         die vermutete Kategorie, wenn keine Regel den Wert erklaert, sonst leer
 * @param erklaert          ob die Kategorie begruendet werden konnte
 * @param konfidenz         wie die Kategorie zustande kam
 * @param feedbackStufe     die ausgegebene Scaffolding-Stufe, -1 ohne Stufung
 * @param historieKategorie unter welcher Kategorie dieser Versuch verbucht wurde
 * @param erfolgeVorher     Erfolge in dieser Kategorie vor diesem Versuch
 * @param fehlerVorher      Fehler in dieser Kategorie vor diesem Versuch
 * @param dauerVersuchSekunden Zeit seit der vorigen Antwort an dieser Aufgabe, beim ersten
 *                          Versuch seit der Aufgabenstellung, in vollen Sekunden
 * @param dauerSekunden     Zeit zwischen Aufgabenstellung und dieser Antwort, in vollen Sekunden
 */
public record Protokolleintrag(
        Instant zeitpunkt,
        String teilnehmerId,
        Teilnehmer.Gruppe gruppe,
        int aufgabenNummer,
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
        String historieKategorie,
        int erfolgeVorher,
        int fehlerVorher,
        long dauerVersuchSekunden,
        long dauerSekunden
) {

    /** Ein Fehler, den keine Fehlregel erklaeren konnte. Kandidat fuer die Bug Library. */
    public boolean unerklaert() {
        return !korrekt && !erklaert;
    }

    /** Gesamtzahl der Versuche in dieser Kategorie vor diesem Eintrag. */
    public int versucheVorher() {
        return erfolgeVorher + fehlerVorher;
    }

    /** Kopfzeile fuer den CSV-Export, passend zu {@link #alsCsvZeile()}. */
    public static String csvKopfzeile() {
        return "zeitpunkt;teilnehmer;gruppe;aufgabenNummer;kategorie;ausdruck;belegung;"
                + "referenz;antwort;versuch;korrekt;diagnose;vermutung;erklaert;konfidenz;"
                + "feedbackStufe;historieKategorie;erfolgeVorher;fehlerVorher;"
                + "dauerVersuchSekunden;dauerSekunden";
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
                zitiere(teilnehmerId),
                zitiere(gruppe.name()),
                String.valueOf(aufgabenNummer),
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
                zitiere(historieKategorie),
                String.valueOf(erfolgeVorher),
                String.valueOf(fehlerVorher),
                String.valueOf(dauerVersuchSekunden),
                String.valueOf(dauerSekunden));
    }

    /** Setzt ein Feld in Anfuehrungszeichen und verdoppelt darin enthaltene Anfuehrungszeichen. */
    private static String zitiere(String feld) {
        String sicher = feld == null ? "" : feld;
        return "\"" + sicher.replace("\"", "\"\"") + "\"";
    }
}
