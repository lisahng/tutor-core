package de.lmu.tutor.session;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlerklassifikator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.feedback.Rueckmeldung;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.student.Kategoriestatistik;
import de.lmu.tutor.student.PfaParameter;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Eine Uebungssitzung: die Fassade ueber alle Komponenten des Tutorsystems.
 *
 * <p>Sie bildet den in Abschnitt 3.2.3 beschriebenen Arbeitsablauf ab und ist die
 * einzige Klasse, die eine Oberflaeche kennen muss:</p>
 *
 * <ol>
 *   <li>{@link #naechsteAufgabe()} laesst das Studentenmodell die schwaechste Kategorie
 *       waehlen und den Generator dazu einen Ausdruck erzeugen. Die korrekte Loesung wird
 *       sofort vorab berechnet und intern gehalten.</li>
 *   <li>{@link #antworte(NutzerAntwort)} vergleicht die Eingabe mit dieser Referenz,
 *       diagnostiziert bei Abweichung die Fehlvorstellung, erzeugt das passende
 *       Feedback, aktualisiert das Studentenmodell und schreibt einen Protokolleintrag.</li>
 * </ol>
 *
 * <p><b>Warum diese Klasse existiert.</b> Ohne sie muesste ein Web-Controller die
 * Reihenfolge der sieben Komponenten selbst kennen. Damit waere der Ablauf nur noch im
 * Browser pruefbar. So laesst sich eine komplette Sitzung in einem Test durchspielen.</p>
 *
 * <p><b>Mehrere Versuche.</b> Eine Aufgabe bleibt gestellt, bis {@link #naechsteAufgabe()}
 * erneut aufgerufen wird. Wer also zweimal falsch antwortet, erzeugt zwei Protokolleintraege
 * zu derselben Aufgabe und erhaelt beim zweiten Mal die naechste Scaffolding-Stufe. Das ist
 * beabsichtigt, denn die Anzahl der Versuche gehoert zu den erhobenen Daten.</p>
 *
 * <p><b>Die beiden Bedingungen.</b> Der Unterschied liegt allein in der Aufgabenauswahl.
 * In der Tutorgruppe waehlt das Studentenmodell die naechste Kategorie nach PFA, also die
 * mit der niedrigsten geschaetzten Erfolgswahrscheinlichkeit, und stellt zu einer
 * erkannten Fehlvorstellung weitere Aufgaben. In der Kontrollgruppe laeuft eine feste
 * Reihenfolge reihum durch alle Kategorien, unabhaengig davon, was die Person richtig oder
 * falsch beantwortet hat. Alles andere ist identisch: dieselbe Rueckmeldung mit denselben
 * Scaffolding-Stufen, dieselbe Zahl moeglicher Versuche, dieselbe Diagnose und dieselbe
 * Protokollierung. Damit unterscheiden sich die Gruppen in genau einer Sache.</p>
 *
 * <p><b>Nicht nebenlaeufig.</b> Eine Sitzung gehoert zu genau einer Person und haelt
 * veraenderlichen Zustand. In einer Webanwendung gehoert sie in die Session, nicht in eine
 * von allen geteilte Bean.</p>
 */
public final class Uebungssitzung {

    private final BugLibrary bibliothek;
    private final AufgabenGenerator generator;
    private final StepEvaluator evaluator;
    private final Fehlerklassifikator klassifikator;
    private final Feedbackgenerator feedbackgenerator;
    private final Studentenmodell studentenmodell;
    private final Clock uhr;

    /** Wer uebt und in welcher Bedingung. Steht in jeder Protokollzeile. */
    private final Teilnehmer teilnehmer;

    private final List<Protokolleintrag> protokoll = new ArrayList<>();

    /** Kategorien, fuer die der Generator keine Vorlage hat. Wird zur Laufzeit gelernt. */
    private final Set<String> ohneVorlage = new HashSet<>();

    /** So oft wird hoechstens neu gezogen, um einen unverbrauchten Ausdruck zu finden. */
    private static final int ANLAEUFE = 25;

    /**
     * Position in der festen Reihenfolge der Kontrollgruppe. Zaehlt ueber die ganze
     * Sitzung weiter und laeuft am Ende der Liste wieder von vorn los, so dass jede
     * Kategorie gleich oft drankommt.
     */
    private int festePosition;

    private Aufgabe aktuelleAufgabe;
    private EvaluationResult aktuelleReferenz;
    private Instant aufgabeGestelltUm;
    private int versucheAnAktuellerAufgabe;

    /**
     * Die bereits gestellten Ausdruecke dieser Sitzung, als Text samt Belegung.
     *
     * <p>Verhindert, dass derselbe Ausdruck zweimal erscheint. Eine Kategorie darf und
     * soll wiederkommen, ein wortgleicher Ausdruck nicht: Beim zweiten Mal wird er
     * erinnert statt ausgewertet, und die Aufgabe misst dann etwas anderes als beim ersten
     * Mal.</p>
     */
    private final Set<String> gestellteAusdruecke = new HashSet<>();

    /**
     * Sitzung mit Standardeinstellungen und zufaelligen Aufgaben, ohne Teilnehmerbezug.
     *
     * <p>Fuer Demos und Tests gedacht. In der Studie immer die Form mit
     * {@link Teilnehmer} nehmen, sonst traegt jede Protokollzeile nur das Pseudonym
     * "anonym" und die Zeilen mehrerer Personen liessen sich nicht trennen.</p>
     */
    public Uebungssitzung(BugLibrary bibliothek) {
        this(bibliothek, Teilnehmer.anonym());
    }

    /** Sitzung fuer eine bestimmte Person, mit Standardeinstellungen. */
    public Uebungssitzung(BugLibrary bibliothek, Teilnehmer teilnehmer) {
        this(bibliothek, teilnehmer, new AufgabenGenerator(), new Feedbackgenerator(),
                new Studentenmodell(bibliothek), Clock.systemDefaultZone());
    }

    /**
     * Sitzung mit festem Zufall, damit Tests reproduzierbar sind.
     *
     * @param seed steuert sowohl die erzeugten Ausdruecke als auch die Auswahl der
     *             Lobformulierungen
     */
    public Uebungssitzung(BugLibrary bibliothek, long seed) {
        this(bibliothek, Teilnehmer.anonym(), new AufgabenGenerator(seed),
                new Feedbackgenerator(new Random(seed)),
                new Studentenmodell(bibliothek, PfaParameter.STANDARD, 1),
                Clock.systemDefaultZone());
    }

    /** Vollstaendig konfigurierbare Sitzung, etwa fuer abweichende PFA-Parameter. */
    public Uebungssitzung(BugLibrary bibliothek,
                          Teilnehmer teilnehmer,
                          AufgabenGenerator generator,
                          Feedbackgenerator feedbackgenerator,
                          Studentenmodell studentenmodell,
                          Clock uhr) {
        this.bibliothek = bibliothek;
        this.teilnehmer = teilnehmer;
        this.generator = generator;
        this.evaluator = new StepEvaluator();
        this.klassifikator = new Fehlerklassifikator(bibliothek);
        this.feedbackgenerator = feedbackgenerator;
        this.studentenmodell = studentenmodell;
        this.uhr = uhr;
    }

    // ---------------------------------------------------------------
    // Ablauf
    // ---------------------------------------------------------------

    /**
     * Stellt die naechste Aufgabe und berechnet ihre Loesung vorab.
     *
     * <p>Die Kategorie waehlt das Studentenmodell nach PFA. Hat der Generator fuer die
     * gewaehlte Kategorie keine Vorlage, wird sie vermerkt und die naechstbeste genommen.
     * Diese Vorsichtsmassnahme kostet nichts, verhindert aber, dass die Sitzung stehen
     * bleibt, sobald die Bug Library um eine Kategorie ohne Vorlage waechst.</p>
     *
     * @return die gestellte Aufgabe
     * @throws IllegalStateException wenn es fuer keine Kategorie eine Vorlage gibt
     */
    public Aufgabe naechsteAufgabe() {
        // Hoechstens so viele Anlaeufe wie es Kategorien gibt: danach ist jede einmal
        // geprueft und ein weiterer Versuch koennte nur endlos kreisen.
        for (int versuch = 0; versuch < bibliothek.size(); versuch++) {
            Optional<Misconception> kategorie = teilnehmer.istKontrollgruppe()
                    ? naechsteFesteKategorie()
                    : studentenmodell.naechsteKategorie();
            if (kategorie.isEmpty()) {
                break;
            }
            String id = kategorie.get().id();
            if (ohneVorlage.contains(id)) {
                continue;
            }
            try {
                stelle(frischeAufgabe(id));
                return aktuelleAufgabe;
            } catch (IllegalArgumentException keineVorlage) {
                ohneVorlage.add(id);
            }
        }
        throw new IllegalStateException(
                "Keine uebbare Kategorie gefunden. Ohne Vorlage: " + ohneVorlage);
    }

    /** Stellt gezielt eine Aufgabe zu einer Kategorie, etwa fuer einen festen Prae-Test. */
    public Aufgabe aufgabeZu(String kategorieId) {
        stelle(frischeAufgabe(kategorieId));
        return aktuelleAufgabe;
    }

    /**
     * Erzeugt zu einer Kategorie einen Ausdruck, der in dieser Sitzung noch nicht vorkam.
     *
     * <p>Der Generator zieht zufaellig, und bei kleinen Zahlenraeumen wiederholt sich ein
     * Ausdruck irgendwann. In einer Sitzung mit vierzig Aufgaben passiert das haeufig
     * genug, um aufzufallen: In einer Stichprobe von vierzig simulierten Sitzungen war
     * mehr als die Haelfte betroffen. Ein zweites Mal derselbe Ausdruck misst aber nicht
     * mehr dasselbe, denn beim zweiten Mal kann man sich an die Loesung erinnern.</p>
     *
     * <p>Nach {@value #ANLAEUFE} vergeblichen Anlaeufen wird der zuletzt erzeugte Ausdruck
     * genommen. Das kommt vor, wenn eine Kategorie nur wenige moegliche Ausdruecke hat und
     * sie alle schon dran waren. Eine Wiederholung ist dann besser als eine Sitzung, die
     * stehen bleibt.</p>
     */
    private Aufgabe frischeAufgabe(String kategorieId) {
        Aufgabe aufgabe = generator.generiere(kategorieId);
        for (int anlauf = 1; anlauf < ANLAEUFE && gestellteAusdruecke.contains(kennung(aufgabe));
                anlauf++) {
            aufgabe = generator.generiere(kategorieId);
        }
        return aufgabe;
    }

    /** Ausdruck samt Belegung, denn bei {@code a[k + 1]} entscheidet erst die Belegung. */
    private static String kennung(Aufgabe aufgabe) {
        return aufgabe.render() + " | " + aufgabe.belegung();
    }

    /**
     * Die naechste Kategorie der Kontrollbedingung, ohne jede Anpassung an die Leistung.
     *
     * <p>Grundlage ist dieselbe Startreihenfolge, mit der auch die Tutorgruppe beginnt,
     * also die Kategorien in der Reihenfolge der Bug Library. Nach der letzten geht es
     * wieder bei der ersten los. Dadurch bekommt jede Kategorie gleich viele Aufgaben,
     * waehrend die Tutorgruppe ihre Zeit auf die schwachen Kategorien verlagert. Genau
     * dieser Unterschied ist der untersuchte Faktor.</p>
     *
     * <p>Die Aufgaben selbst sind trotzdem jedes Mal neu, denn der Generator erzeugt zu
     * einer Kategorie bei jedem Aufruf einen anderen Ausdruck. Wiederholt wird also die
     * Kategorie, nicht die Aufgabe.</p>
     */
    private Optional<Misconception> naechsteFesteKategorie() {
        List<String> reihenfolge = studentenmodell.startreihenfolge();
        if (reihenfolge.isEmpty()) {
            return Optional.empty();
        }
        // Hoechstens eine volle Runde: Sind alle IDs der Reihenfolge unbekannt, bricht die
        // Schleife ab, statt endlos zu kreisen.
        for (int i = 0; i < reihenfolge.size(); i++) {
            String id = reihenfolge.get(Math.floorMod(festePosition, reihenfolge.size()));
            festePosition++;
            Optional<Misconception> m = bibliothek.byId(id);
            if (m.isPresent()) {
                return m;
            }
        }
        return Optional.empty();
    }

    private void stelle(Aufgabe aufgabe) {
        this.aktuelleAufgabe = aufgabe;
        this.aktuelleReferenz = evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
        this.aufgabeGestelltUm = uhr.instant();
        this.versucheAnAktuellerAufgabe = 0;
        this.gestellteAusdruecke.add(kennung(aufgabe));
    }

    /**
     * Wertet eine Eingabe zur aktuellen Aufgabe aus.
     *
     * <p>Reihenfolge und Begruendung: Zuerst diagnostizieren, dann die Wiederholungszahl
     * <em>vor</em> diesem Versuch holen und zugleich das Studentenmodell fortschreiben,
     * erst dann das Feedback erzeugen. Wuerde das Modell vorher aktualisiert, zaehlte der
     * gerade gemachte Fehler bereits mit und die Person wuerde beim ersten Fehler die
     * allgemeine Stufe 0 ueberspringen.</p>     *
     * @throws IllegalStateException wenn noch keine Aufgabe gestellt wurde
     */
    public Antwortergebnis antworte(NutzerAntwort antwort) {
        if (aktuelleAufgabe == null) {
            throw new IllegalStateException("Es ist keine Aufgabe gestellt. Zuerst naechsteAufgabe() aufrufen.");
        }
        versucheAnAktuellerAufgabe++;

        Diagnose diagnose = klassifikator.diagnostiziere(aktuelleAufgabe, aktuelleReferenz, antwort);

        // Die Historie wird unter der diagnostizierten Kategorie gefuehrt, nicht unter der
        // Zielkategorie der Aufgabe. Wer bei einer Array-Aufgabe an der Praezedenz scheitert,
        // soll Praezedenz ueben, nicht Arrays.
        String kategorieFuerHistorie = diagnose.misconception()
                .map(Misconception::id)
                .orElse(aktuelleAufgabe.kategorieId());

        // Die Zaehlerstaende VOR dem Verbuchen festhalten. Genau diese beiden Zahlen
        // braucht die spaetere Regression, mit der gamma und rho aus den Logdaten
        // geschaetzt werden. Nach dem Verbuchen waere der aktuelle Versuch schon drin.
        int erfolgeVorher = studentenmodell.erfolge(kategorieFuerHistorie);
        int fehlerVorher = studentenmodell.fehler(kategorieFuerHistorie);

        studentenmodell.erfasseErgebnis(kategorieFuerHistorie, diagnose.korrekt());

        // Die Scaffolding-Stufe richtet sich nach den Fehlversuchen an DIESER Aufgabe,
        // nicht nach der Fehlerhistorie der ganzen Sitzung und auch nicht nach der
        // diagnostizierten Kategorie. Weil eine richtige Antwort die Aufgabe beendet,
        // waren alle vorherigen Versuche Fehlversuche.
        //
        // Frueher wurde je diagnostizierter Kategorie gezaehlt. Das hatte eine unschoene
        // Folge: Wechselte die Diagnose zwischen zwei Versuchen, begann die Stufung wieder
        // bei 0, und die Person bekam zweimal hintereinander denselben allgemeinen
        // Hinweis. Bei drei erlaubten Versuchen konnte sie die ausgerechnete Loesung dann
        // gar nicht mehr erreichen. Ausserdem war die protokollierte Stufe nicht mehr
        // monoton und damit schwer auszuwerten.
        int fehlversuche = versucheAnAktuellerAufgabe - 1;

        // Die Rueckmeldung ist in beiden Bedingungen dieselbe. Unterschieden wird allein
        // bei der Auswahl der naechsten Aufgabe, siehe naechsteFesteKategorie().
        Rueckmeldung rueckmeldung = feedbackgenerator.erstelle(
                diagnose, fehlversuche, aktuelleAufgabe, aktuelleReferenz);
        protokolliere(antwort, diagnose, rueckmeldung,
                kategorieFuerHistorie, erfolgeVorher, fehlerVorher);

        return new Antwortergebnis(diagnose.korrekt(), rueckmeldung, diagnose,
                aktuelleReferenz, versucheAnAktuellerAufgabe);
    }

    private void protokolliere(NutzerAntwort antwort, Diagnose diagnose, Rueckmeldung rueckmeldung,
                               String historieKategorie, int erfolgeVorher, int fehlerVorher) {
        Instant jetzt = uhr.instant();
        // Bei einer Kombination stehen alle beteiligten IDs in der Spalte, verbunden
        // mit einem Pluszeichen, etwa B01+B02.
        String kategorie = diagnose.kategorieIds();

        // Nur begruendete Diagnosen stehen in der Diagnose-Spalte. Konnte keine Fehlregel
        // den Wert erklaeren, wandert die Kategorie in die Vermutungs-Spalte, damit die
        // Trefferquote in Strang 3 nicht durch Zufallstreffer geschoent wird.
        String diagnoseId = diagnose.erklaert() ? kategorie : "";
        String vermutung = diagnose.erklaert() || diagnose.korrekt() ? "" : kategorie;

        protokoll.add(new Protokolleintrag(
                jetzt,
                teilnehmer.id(),
                teilnehmer.gruppe(),
                aktuelleAufgabe.kategorieId(),
                aktuelleAufgabe.render(),
                aktuelleAufgabe.belegung(),
                aktuelleReferenz.ergebnisText(),
                antwortAlsText(antwort),
                versucheAnAktuellerAufgabe,
                diagnose.korrekt(),
                diagnoseId,
                vermutung,
                diagnose.erklaert(),
                diagnose.konfidenz(),
                rueckmeldung.stufe(),
                historieKategorie,
                erfolgeVorher,
                fehlerVorher,
                sekundenSeit(aufgabeGestelltUm, jetzt)));
    }

    /**
     * Die vergangene Zeit in vollen Sekunden, kaufmaennisch gerundet.
     *
     * <p>Millisekunden waeren Scheingenauigkeit. Gemessen wird, wie lange jemand ueber
     * einer Aufgabe sitzt, und das haengt am Nachdenken und am Tippen, nicht am
     * Netzwerk. Sekunden sind ausserdem die Einheit, in der die Auswertung rechnet.</p>
     */
    private static long sekundenSeit(Instant anfang, Instant ende) {
        return Math.round((ende.toEpochMilli() - anfang.toEpochMilli()) / 1000.0);
    }

    private static String antwortAlsText(NutzerAntwort antwort) {
        return antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar";
    }

    // ---------------------------------------------------------------
    // Abfragen
    // ---------------------------------------------------------------

    /** Die gerade gestellte Aufgabe, oder ein leeres Optional vor der ersten. */
    public Optional<Aufgabe> aktuelleAufgabe() {
        return Optional.ofNullable(aktuelleAufgabe);
    }

    /**
     * Die vorab berechnete Musterloesung zur aktuellen Aufgabe.
     *
     * <p>Eine Oberflaeche darf sie erst nach einer Antwort zeigen. Der Zugriff ist trotzdem
     * noetig, und zwar aus einem konkreten Grund: Ausdruecke mit {@code ++} veraendern die
     * Variablenbelegung. Wuerde jemand den Ausdruck ein zweites Mal auswerten, um die
     * Loesung zu erhalten, bekaeme er ein anderes Ergebnis. Die Loesung muss also von genau
     * der einen Auswertung stammen, die bei der Aufgabenstellung passiert ist.</p>
     */
    public Optional<EvaluationResult> musterloesung() {
        return Optional.ofNullable(aktuelleReferenz);
    }

    /** Wie oft an der aktuellen Aufgabe schon geantwortet wurde. */
    public int versucheAnAktuellerAufgabe() {
        return versucheAnAktuellerAufgabe;
    }

    /** Das Systemprotokoll der Sitzung, in zeitlicher Reihenfolge. */
    public List<Protokolleintrag> protokoll() {
        return Collections.unmodifiableList(protokoll);
    }

    /** Das Protokoll als CSV mit Kopfzeile, fuer die Auswertung der Studie. */
    public String protokollAlsCsv() {
        StringBuilder csv = new StringBuilder(Protokolleintrag.csvKopfzeile());
        for (Protokolleintrag eintrag : protokoll) {
            csv.append(System.lineSeparator()).append(eintrag.alsCsvZeile());
        }
        return csv.toString();
    }

    /** Der Kenntnisstand ueber alle Kategorien. */
    public List<Kategoriestatistik> kenntnisstand() {
        return studentenmodell.alleStatistiken();
    }

    /** Anzahl richtig beantworteter Versuche in dieser Sitzung. */
    public int richtigeAntworten() {
        return (int) protokoll.stream().filter(Protokolleintrag::korrekt).count();
    }

    /**
     * Die Fehler, die keine Fehlregel erklaeren konnte.
     *
     * <p>Fuer die Auswertung der Studie die aufschlussreichste Teilmenge: Sie zeigt,
     * welche Fehlvorstellungen die Bug Library noch nicht abbildet. In der strengen
     * Trefferquote bleiben diese Faelle aussen vor.</p>
     */
    public List<Protokolleintrag> unerklaerteFehler() {
        return protokoll.stream().filter(Protokolleintrag::unerklaert).toList();
    }

    /** Anteil der Fehler, zu denen eine begruendete Diagnose vorliegt; ohne Fehler 0. */
    public double anteilErklaerterFehler() {
        long fehler = protokoll.stream().filter(e -> !e.korrekt()).count();
        if (fehler == 0) {
            return 0.0;
        }
        long erklaert = protokoll.stream().filter(e -> !e.korrekt() && e.erklaert()).count();
        return (double) erklaert / fehler;
    }

    /** Wer in dieser Sitzung uebt und in welcher Bedingung. */
    public Teilnehmer teilnehmer() {
        return teilnehmer;
    }

    /** Das Studentenmodell der Sitzung, etwa fuer eine Fortschrittsanzeige. */
    public Studentenmodell studentenmodell() {
        return studentenmodell;
    }
}
