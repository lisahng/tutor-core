package de.lmu.tutor.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import de.lmu.tutor.ast.JType;
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
    private String letzteEingabe = "";

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
     * <p>Eine nicht lesbare Eingabe wird nicht gewertet, sondern mit einem Hinweis
     * zurueckgewiesen. Der Versuchszaehler und die Fehlerhistorie bleiben unberuehrt, denn
     * ein Tippfehler ist keine Fehlvorstellung.</p>
     *
     * @return {@code true}, wenn die Eingabe gewertet wurde
     */
    public boolean antworte(String eingabe) {
        if (zustand != Zustand.AUFGABE_OFFEN) {
            return false;
        }
        letzteEingabe = eingabe == null ? "" : eingabe;

        Optional<NutzerAntwort> gelesen = Antwortparser.lies(eingabe, erwarteterTyp());
        if (gelesen.isEmpty()) {
            eingabehinweis = "Diese Eingabe konnte ich nicht lesen. Bitte gib einen Java-Wert ein, "
                    + "etwa 7, 2.5, true, 'a', \"abc\" oder \"nicht auswertbar\".";
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
        if (zustand != Zustand.AUFGABE_ERLEDIGT) {
            return;
        }
        if (aufgabenNummer >= plan.maximaleAufgaben() || zeitAbgelaufen()) {
            zustand = Zustand.BEENDET;
            return;
        }
        sitzung.naechsteAufgabe();
        aufgabenNummer++;
        letztesErgebnis = null;
        letzteEingabe = "";
        eingabehinweis = "";
        zustand = Zustand.AUFGABE_OFFEN;
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

    public String letzteEingabe() {
        return letzteEingabe;
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

    private Optional<JType> erwarteterTyp() {
        return sitzung.musterloesung()
                .filter(EvaluationResult::auswertbar)
                .map(r -> r.wert().typ());
    }
}
