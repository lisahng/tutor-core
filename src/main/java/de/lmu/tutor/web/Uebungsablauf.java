package de.lmu.tutor.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.session.Antwortergebnis;
import de.lmu.tutor.session.Protokolleintrag;
import de.lmu.tutor.session.Teilnehmer;
import de.lmu.tutor.session.Uebungssitzung;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Der Ablauf einer Uebungssitzung im Browser.
 *
 * <p>Die {@link Uebungssitzung} weiss, welche Aufgabe als naechstes kommt und welche
 * Rueckmeldung eine Antwort verdient. Was sie nicht weiss, ist alles, was erst durch die
 * Weboberflaeche entsteht: wann die Sitzung begonnen hat, wieviel Zeit noch bleibt, die
 * wievielte Aufgabe gerade laeuft, und ob die aktuelle Aufgabe abgeschlossen ist oder noch
 * ein Versuch offensteht. Genau das haelt diese Klasse.</p>
 *
 * <p><b>Warum das nicht im Controller steht.</b> Ein Spring-Controller laesst sich nur mit
 * laufendem Webserver testen. Der Ablauf dagegen ist gewoehnliches Java und damit im selben
 * Rahmen pruefbar wie der Rest der Arbeit. Der Controller bleibt eine duenne Schicht, die
 * Formulareingaben hereinreicht und Ansichtsdaten herausgibt.</p>
 *
 * <p><b>Zur Uhr.</b> Sie wird hereingereicht statt {@code Instant.now()} aufzurufen, damit
 * sich der Zeitablauf im Test ohne Warten pruefen laesst.</p>
 *
 * <p>Die Klasse ist nicht threadsicher. Sie gehoert zu genau einer Browsersitzung und wird
 * dort nur von einem Anfragefaden zugleich benutzt.</p>
 */
public final class Uebungsablauf {

    /** Was gerade zu tun ist. */
    public enum Zustand {
        /** Die Arbeitsphase hat noch nicht begonnen. */
        BEREIT,
        /** Eine Aufgabe ist offen und wartet auf eine Antwort. */
        AUFGABE_OFFEN,
        /** Die Aufgabe ist erledigt, es geht weiter oder zum Abschluss. */
        AUFGABE_ERLEDIGT,
        /** Zeit oder Aufgabenzahl sind aufgebraucht. */
        BEENDET
    }

    private final Uebungssitzung sitzung;
    private final Sitzungsplan plan;
    private final Clock uhr;

    private Zustand zustand = Zustand.BEREIT;
    private Instant beginn;
    private int aufgabenNummer;
    private Antwortergebnis letztesErgebnis;
    private String eingabehinweis = "";
    private String letzterTyp = "";
    private String letzterWert = "";
    private final List<Feedbackbewertung> bewertungen = new ArrayList<>();

    /**
     * @param bibliothek die Bug Library
     * @param teilnehmer Pseudonym und Gruppe
     * @param plan       Zeit- und Aufgabenbudget
     * @param seed       Startwert des Aufgabengenerators
     * @param uhr        die Uhr fuer die Restzeit
     */
    public Uebungsablauf(BugLibrary bibliothek, Teilnehmer teilnehmer, Sitzungsplan plan,
                         long seed, Clock uhr) {
        this.plan = plan;
        this.uhr = uhr;
        this.sitzung = new Uebungssitzung(bibliothek, teilnehmer,
                new AufgabenGenerator(seed),
                new de.lmu.tutor.feedback.Feedbackgenerator(new java.util.Random(seed)),
                new Studentenmodell(bibliothek),
                uhr);
    }

    // ================================================================
    // Ablauf
    // ================================================================

    /** Stellt die erste Aufgabe und startet die Zeitmessung. */
    public void starte() {
        if (zustand != Zustand.BEREIT) {
            return;
        }
        beginn = uhr.instant();
        sitzung.naechsteAufgabe();
        aufgabenNummer = 1;
        zustand = Zustand.AUFGABE_OFFEN;
    }

    /**
     * Wertet eine Eingabe aus.
     *
     * <p>Gefragt wird nach Typ und Wert, getrennt, wie in den Klausuraufgaben. Eine nicht
     * lesbare Eingabe wird nicht gewertet, sondern mit einem Hinweis zurueckgewiesen. Der
     * Versuchszaehler und die Fehlerhistorie bleiben unberuehrt, denn ein Tippfehler ist
     * keine Fehlvorstellung.</p>
     *
     * @param typ  der gewaehlte Typ, etwa {@code "int"}, oder "nicht auswertbar"
     * @param wert der getippte Wert
     * @return {@code true}, wenn die Eingabe gewertet wurde
     */
    public boolean antworte(String typ, String wert) {
        if (zustand != Zustand.AUFGABE_OFFEN) {
            return false;
        }
        letzterTyp = typ == null ? "" : typ;
        letzterWert = wert == null ? "" : wert;

        Optional<NutzerAntwort> gelesen = Antwortparser.lies(typ, wert);
        if (gelesen.isEmpty()) {
            eingabehinweis = Antwortparser.hinweisZu(typ, wert);
            return false;
        }

        eingabehinweis = "";
        letztesErgebnis = sitzung.antworte(gelesen.get());

        boolean versucheAufgebraucht = letztesErgebnis.versuch() >= plan.maximaleVersuche();
        if (letztesErgebnis.korrekt() || versucheAufgebraucht) {
            zustand = Zustand.AUFGABE_ERLEDIGT;
        }
        return true;
    }

    /**
     * Geht zur naechsten Aufgabe oder beendet die Arbeitsphase.
     *
     * <p>Die Arbeitsphase endet, sobald entweder das Aufgabenbudget aufgebraucht ist oder
     * die Zeit abgelaufen ist. Gepruefte wird erst hier und nicht mitten in einer Aufgabe,
     * damit niemand beim Tippen unterbrochen wird.</p>
     */
    public void weiter() {
        weiter(Feedbackbewertung.UEBERSPRUNGEN);
    }

    /**
     * Geht zur naechsten Aufgabe und haelt dabei fest, wie hilfreich die Rueckmeldung war.
     *
     * <p>Die Bewertung haengt an der Aufgabe und nicht am einzelnen Versuch. Sie wird nur
     * dann aufgenommen, wenn es ueberhaupt eine Rueckmeldung auf einen Fehler gab, denn
     * ein Lob fuer eine auf Anhieb richtige Antwort ist keine Hilfestellung, die sich
     * bewerten liesse.</p>
     *
     * @param bewertung 1 bis 5, oder {@link Feedbackbewertung#UEBERSPRUNGEN}
     */
    public void weiter(int bewertung) {
        if (zustand != Zustand.AUFGABE_ERLEDIGT) {
            return;
        }
        erfasseBewertung(bewertung);
        if (aufgabenNummer >= plan.maximaleAufgaben() || zeitAbgelaufen()) {
            zustand = Zustand.BEENDET;
            return;
        }
        sitzung.naechsteAufgabe();
        aufgabenNummer++;
        letztesErgebnis = null;
        letzterTyp = "";
        letzterWert = "";
        eingabehinweis = "";
        zustand = Zustand.AUFGABE_OFFEN;
    }

    /**
     * Schreibt eine Bewertung der Rueckmeldung zur gerade erledigten Aufgabe mit.
     *
     * <p>Wer auf Anhieb richtig liegt, bekommt keine Frage gestellt und erzeugt auch keine
     * Zeile. Sonst waere die Haelfte der Daten eine Bewertung von Lob.</p>
     */
    private void erfasseBewertung(int bewertung) {
        if (letztesErgebnis == null || bewertungGefragtEntfaellt()) {
            return;
        }
        Antwortergebnis e = letztesErgebnis;
        bewertungen.add(new Feedbackbewertung(
                uhr.instant(),
                teilnehmer().id(),
                teilnehmer().gruppe(),
                aufgabenNummer,
                sitzung.aktuelleAufgabe().map(Aufgabe::kategorieId).orElse(""),
                ausdruck(),
                e.versuch(),
                e.korrekt(),
                e.rueckmeldung().stufe(),
                // Nur eine begruendete Diagnose wird genannt. Eine blosse Vermutung aus
                // der Herkunft der Aufgabe gehoert nicht in dieselbe Spalte, sonst waere
                // die Trefferquote geschoent. Dieselbe Trennung wie im Systemprotokoll.
                e.diagnose().erklaert() ? e.diagnose().kategorieIds() : "",
                e.diagnose().erklaert(),
                begrenzeBewertung(bewertung)));
    }

    /** Nach einer auf Anhieb richtigen Antwort gab es keine Rueckmeldung zu bewerten. */
    public boolean bewertungGefragtEntfaellt() {
        return letztesErgebnis != null && letztesErgebnis.korrekt()
                && letztesErgebnis.versuch() == 1;
    }

    /** Ob nach dieser Aufgabe nach der Nuetzlichkeit der Rueckmeldung gefragt wird. */
    public boolean fragtNachBewertung() {
        return zustand == Zustand.AUFGABE_ERLEDIGT && !bewertungGefragtEntfaellt();
    }

    private static int begrenzeBewertung(int bewertung) {
        if (bewertung < Feedbackbewertung.KLEINSTE || bewertung > Feedbackbewertung.GROESSTE) {
            return Feedbackbewertung.UEBERSPRUNGEN;
        }
        return bewertung;
    }

    /** Die bisher abgegebenen Bewertungen, aelteste zuerst. */
    public List<Feedbackbewertung> bewertungen() {
        return List.copyOf(bewertungen);
    }

    /** Die Bewertungen als CSV, passend zum Systemprotokoll. */
    public String bewertungenAlsCsv() {
        StringBuilder sb = new StringBuilder(Feedbackbewertung.csvKopfzeile());
        for (Feedbackbewertung b : bewertungen) {
            sb.append(System.lineSeparator()).append(b.alsCsvZeile());
        }
        return sb.toString();
    }

    /** Beendet die Arbeitsphase vorzeitig, etwa wenn die Versuchsleitung abbricht. */
    public void beende() {
        zustand = Zustand.BEENDET;
    }

    // ================================================================
    // Ansichtsdaten
    // ================================================================

    public Zustand zustand() {
        return zustand;
    }

    public boolean beendet() {
        return zustand == Zustand.BEENDET;
    }

    public Sitzungsplan plan() {
        return plan;
    }

    public Teilnehmer teilnehmer() {
        return sitzung.teilnehmer();
    }

    public int aufgabenNummer() {
        return aufgabenNummer;
    }

    public Optional<Aufgabe> aufgabe() {
        return sitzung.aktuelleAufgabe();
    }

    /** Der gestellte Ausdruck als Java-Quelltext, leer wenn keine Aufgabe offen ist. */
    public String ausdruck() {
        return sitzung.aktuelleAufgabe().map(Aufgabe::render).orElse("");
    }

    /** Die Variablenbelegung als Text, leer wenn der Ausdruck ohne Variablen auskommt. */
    public String belegung() {
        return sitzung.aktuelleAufgabe().map(Aufgabe::belegung).orElse("");
    }

    public Optional<Antwortergebnis> letztesErgebnis() {
        return Optional.ofNullable(letztesErgebnis);
    }

    public String eingabehinweis() {
        return eingabehinweis;
    }

    /** Der zuletzt gewaehlte Typ, damit die Auswahl nach einem Fehlversuch stehen bleibt. */
    public String letzterTyp() {
        return letzterTyp;
    }

    /** Der zuletzt getippte Wert, damit das Feld nach einem Fehlversuch gefuellt bleibt. */
    public String letzterWert() {
        return letzterWert;
    }

    /** Die Typen, die zur Auswahl stehen. */
    public List<String> typen() {
        return Antwortparser.typen();
    }

    /** Der wievielte Versuch an der laufenden Aufgabe als naechstes faellig waere. */
    public int versuche() {
        return sitzung.versucheAnAktuellerAufgabe();
    }

    /** Die Musterloesung als Text. Wird erst nach dem letzten Versuch angezeigt. */
    public String musterloesung() {
        return sitzung.musterloesung().map(EvaluationResult::ergebnisText).orElse("");
    }

    /** Die verbleibende Arbeitszeit in Sekunden, nie negativ. */
    public long restsekunden() {
        if (beginn == null) {
            return plan.arbeitszeitSekunden();
        }
        long verbraucht = Duration.between(beginn, uhr.instant()).toSeconds();
        return Math.max(0, plan.arbeitszeitSekunden() - verbraucht);
    }

    public boolean zeitAbgelaufen() {
        return restsekunden() <= 0;
    }

    public int richtigeAntworten() {
        return sitzung.richtigeAntworten();
    }

    public List<Protokolleintrag> protokoll() {
        return sitzung.protokoll();
    }

    public String protokollAlsCsv() {
        return sitzung.protokollAlsCsv();
    }

    /** Die zugrunde liegende Sitzung, fuer Auswertungen am Ende. */
    public Uebungssitzung sitzung() {
        return sitzung;
    }

}
