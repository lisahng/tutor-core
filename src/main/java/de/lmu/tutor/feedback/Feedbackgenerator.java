package de.lmu.tutor.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlersimulator;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Schritt 6 der Systemarchitektur: gibt kontextadaptives Feedback auf Basis des erkannten
 * Fehlertyps.
 *
 * <p><b>Drei Modi.</b> Welche Rueckmeldung erscheint, haengt davon ab, was die Diagnose
 * hergibt.</p>
 *
 * <p><i>Begruendete Einzeldiagnose.</i> Eine Fehlregel hat den eingegebenen Wert
 * nachgerechnet oder die Signatur der Antwort ist eindeutig. Dann greift das Scaffolding
 * nach Bruner (1960) mit den Texten aus der Bug Library: erst Aufmerksamkeit lenken, dann
 * die Java-Regel nennen, dann den Schritt am vorliegenden Ausdruck vorrechnen.</p>
 *
 * <p><i>Fehlerkombination.</i> Der Wert laesst sich nur erklaeren, wenn zwei
 * Fehlvorstellungen zusammenkommen. Die Stufung bleibt dieselbe, spricht aber beide an.
 * Stufe 0 weist darauf hin, dass mehr als eine Sache zusammenkommt, ohne sie zu benennen.
 * Stufe 1 nennt beide Regeln, Stufe 2 rechnet beide am Ausdruck vor.</p>
 *
 * <p><i>Unerklaerter Fehler.</i> Keine Regel passt. Dann wird keine Kategorie behauptet.
 * Stattdessen arbeitet das Feedback mit den Zwischenschritten: Es fragt zuerst nach einem
 * Tippfehler, dann nach einem konkreten Zwischenwert und zeigt zuletzt den vollstaendigen
 * Auswertungspfad. Nach VanLehn ist ein einzelner unerklaerbarer Wert eher ein
 * Ausrutscher als eine systematisch falsche Regel. Wer sich vertippt hat, bekaeme sonst
 * eine Belehrung ueber ein Konzept, das er laengst beherrscht.</p>
 *
 * <p><b>Scaffolding-Stufe.</b> Der Generator zaehlt nicht selbst mit, er bekommt die Zahl
 * der bisherigen Fehlversuche uebergeben. Diese Zahl bezieht sich auf die aktuelle
 * Aufgabe: Bei einer neuen Aufgabe beginnt es wieder bei Stufe 0, auch wenn dieselbe
 * Fehlvorstellung vorher schon aufgetreten ist.</p>
 */
public final class Feedbackgenerator {

    /**
     * Die Rueckmeldung auf eine richtige Antwort, immer dieselbe.
     *
     * <p>Fruehere Fassungen waehlten zufaellig aus vier Formulierungen. Das war gut
     * gemeint, aber fuer eine Studie unbrauchbar: Lob ist eine Rueckmeldung wie jede
     * andere, und ob jemand "Richtig!" oder "Sehr gut, das passt!" liest, ist dann eine
     * Groesse, die zwischen den Personen schwankt, ohne dass sie jemand kontrolliert.
     * Eine Person koennte aus der waermeren Formulierung mehr Bestaetigung ziehen als die
     * naechste aus der knappen, und dieser Unterschied landete unbemerkt in den Daten.</p>
     */
    private static final String LOB = "Richtig!";

    /** Ausweichtext, wenn sich die Platzhalter einer Stufe nicht aufloesen lassen. */
    private static final String AUSWEICHTEXT =
            "Werte den Ausdruck Schritt fuer Schritt von innen nach aussen aus und achte dabei "
                    + "auf die Datentypen der Teilergebnisse.";

    // ---- Kombination mehrerer Fehlvorstellungen ----

    private static final String KOMBI_0 =
            "Das stimmt noch nicht, und es haengt an mehr als einer Stelle. Geh {ausdruck} "
                    + "noch einmal von vorn durch und achte auf jeden Teilschritt einzeln.";

    private static final String KOMBI_0_OHNE_AUSDRUCK =
            "Das stimmt noch nicht, und es haengt an mehr als einer Stelle. Geh den Ausdruck "
                    + "noch einmal von vorn durch und achte auf jeden Teilschritt einzeln.";

    // ---- Model Tracing fuer unerklaerte Fehler ----

    private static final String TRACING_0 =
            "Das Ergebnis stimmt noch nicht. Dein Wert laesst sich keinem typischen "
                    + "Missverstaendnis zuordnen, pruefe deine Eingabe also zuerst auf einen "
                    + "Vertipper.";

    private static final String TRACING_1_MIT_SCHRITT =
            "Geh den Ausdruck von innen nach aussen durch. Welchen Wert hat "
                    + "{innererTeilausdruck} bei dir?";

    private static final String TRACING_1_OHNE_SCHRITT =
            "Geh {ausdruck} Schritt fuer Schritt durch und achte dabei auch auf die "
                    + "Datentypen der Operanden, nicht nur auf den Zahlenwert.";

    private static final String TRACING_2_AUSWERTBAR =
            "Die korrekte Auswertung verlaeuft so: {schritte}. Vergleiche das mit deinem "
                    + "eigenen Weg und schau, an welcher Stelle er abweicht.";

    private static final String TRACING_2_NICHT_AUSWERTBAR =
            "{ausdruck} laesst sich gar nicht auswerten: {grund}.";

    private static final String TRACING_OHNE_AUFGABE =
            "Das Ergebnis stimmt noch nicht. Geh den Ausdruck Schritt fuer Schritt von innen "
                    + "nach aussen durch und vergleiche deinen Weg mit dem erwarteten.";

    // Nur fuer Fehlerkombinationen: findet den Teilausdruck, auf den sich die zweite
    // Fehlvorstellung bezieht. Siehe platzhalterFuer.
    private final Fehlersimulator simulator = new Fehlersimulator();
    private final StepEvaluator auswerter = new StepEvaluator();

    public Feedbackgenerator() {
    }

    // ---------------------------------------------------------------
    // Oeffentliche Schnittstelle
    // ---------------------------------------------------------------

    /**
     * Erstellt die Rueckmeldung mit Bezug auf die gestellte Aufgabe. Diese Fassung ist der
     * Normalfall, weil nur sie Platzhalter fuellen und den Auswertungspfad zeigen kann.
     *
     * @param diagnose              Ergebnis des Fehlerklassifikators
     * @param fehlversucheAnAufgabe wie oft an dieser Aufgabe schon falsch geantwortet
     *                              wurde, 0 beim ersten Mal
     * @param aufgabe               die gestellte Aufgabe
     * @param referenz              deren vorab berechnete Loesung
     */
    public Rueckmeldung erstelle(Diagnose diagnose, int fehlversucheAnAufgabe,
                                 Aufgabe aufgabe, EvaluationResult referenz) {
        return erstelle(diagnose, fehlversucheAnAufgabe, new Platzhalter(aufgabe, referenz),
                referenz.auswertbar(), aufgabe);
    }

    /** Erstellt die Rueckmeldung ohne Bezug auf eine Aufgabe. */
    public Rueckmeldung erstelle(Diagnose diagnose, int fehlversucheAnAufgabe) {
        return erstelle(diagnose, fehlversucheAnAufgabe, null, true, null);
    }

    /** Letzte Scaffolding-Stufe unabhaengig von der Zahl der Fehlversuche. */
    public Rueckmeldung erstelleLoesungshilfe(Misconception m, Aufgabe aufgabe, EvaluationResult referenz) {
        int letzte = m.anzahlFeedbackStufen() - 1;
        String text = aufloesen(m.feedbackStufe(letzte), new Platzhalter(aufgabe, referenz));
        return Rueckmeldung.fehler(text, m.id(), letzte);
    }

    /** Wie oben, ohne Aufgabenbezug. */
    public Rueckmeldung erstelleLoesungshilfe(Misconception m) {
        int letzte = m.anzahlFeedbackStufen() - 1;
        return Rueckmeldung.fehler(aufloesen(m.feedbackStufe(letzte), null), m.id(), letzte);
    }

    // ---------------------------------------------------------------
    // Innenleben
    // ---------------------------------------------------------------

    private Rueckmeldung erstelle(Diagnose diagnose, int fehlversuche,
                                  Platzhalter platzhalter, boolean auswertbar, Aufgabe aufgabe) {
        if (diagnose.korrekt()) {
            return Rueckmeldung.korrekt(LOB);
        }
        if (!diagnose.erklaert()) {
            return modellabgleich(fehlversuche, platzhalter, auswertbar);
        }
        if (diagnose.istKombination()) {
            return ausKombination(diagnose, fehlversuche, platzhalter, aufgabe);
        }
        return ausBugLibrary(diagnose, fehlversuche, platzhalter);
    }

    /** Scaffolding mit den Texten der Bug Library, bei einer einzelnen Fehlvorstellung. */
    private Rueckmeldung ausBugLibrary(Diagnose diagnose, int fehlversuche, Platzhalter platzhalter) {
        Misconception m = diagnose.misconception().orElseThrow();
        int stufe = begrenze(fehlversuche, m.anzahlFeedbackStufen());
        String text = aufloesen(m.feedbackStufe(stufe), platzhalter);

        if (diagnose.subtype().isPresent()) {
            Subtype s = diagnose.subtype().get();
            text += " (" + s.name() + ", z. B. " + s.beispiel() + ")";
        }
        return Rueckmeldung.fehler(text, m.id(), stufe);
    }

    /**
     * Scaffolding bei einer Fehlerkombination.
     *
     * <p>Die Stufung bleibt dieselbe, nur spricht sie beide Fehlvorstellungen an. Stufe 0
     * nennt bewusst noch keine der beiden, sondern weist nur darauf hin, dass es an mehr
     * als einer Stelle haengt. Das soll die Aufmerksamkeit lenken, ohne gleich zwei
     * Regeln auf einmal zu erklaeren.</p>
     */
    private Rueckmeldung ausKombination(Diagnose diagnose, int fehlversuche,
                                        Platzhalter platzhalter, Aufgabe aufgabe) {
        List<Misconception> kategorien = diagnose.kategorien();
        int stufen = kategorien.stream().mapToInt(Misconception::anzahlFeedbackStufen).min().orElse(1);
        int stufe = begrenze(fehlversuche, stufen);

        String text;
        if (stufe == 0) {
            text = platzhalter == null
                    ? KOMBI_0_OHNE_AUSDRUCK
                    : platzhalter.fuelle(KOMBI_0).orElse(KOMBI_0_OHNE_AUSDRUCK);
        } else {
            text = kategorien.stream()
                    .map(m -> aufloesen(m.feedbackStufe(stufe), platzhalterFuer(m, aufgabe, platzhalter)))
                    .collect(Collectors.joining(" "));
        }
        return Rueckmeldung.fehler(text, diagnose.kategorieIds(), stufe);
    }

    /**
     * Waehlt die Platzhalter, mit denen der Text einer Kategorie gefuellt wird.
     *
     * <p>Bei einer einzelnen Diagnose betrifft die Fehlvorstellung den ganzen Ausdruck,
     * und die Platzhalter der Aufgabe passen. Bei einer Kombination gilt das nur fuer eine
     * der beiden. Die zweite betrifft fast immer einen Teilausdruck: Bei
     * {@code 7 + 20 % 6} etwa ist die Wurzel ein Pluszeichen, der Modulo-Fehler steckt in
     * {@code 20 % 6}. Werden die Platzhalter trotzdem aus der Wurzel gefuellt, entsteht
     * daraus der Satz "Teilt man 7 durch 20 % 6, bleibt der Rest 9", der weder richtig
     * noch verstaendlich ist.</p>
     *
     * <p>Gesucht wird deshalb der kleinste Teilausdruck, auf dem die Regel dieser
     * Kategorie den Wert tatsaechlich veraendert. Genau dort hat die Lernende anders
     * gerechnet, und genau darueber soll der Satz sprechen. Findet sich keiner, bleibt es
     * bei den Platzhaltern der ganzen Aufgabe.</p>
     *
     * <p>Dass die Regel den Wert aendern muss, ist der entscheidende Teil der Bedingung.
     * Der Simulator meldet eine Regel auch dort, wo sie wirkungslos bleibt: Von links nach
     * rechts zu rechnen ergibt bei einem einzelnen Operator wie {@code 20 % 6} genau das
     * richtige Ergebnis. Ohne diese Pruefung wuerde der Praezedenzfehler faelschlich dem
     * Teilausdruck zugeordnet, obwohl er die Klammerung des ganzen Ausdrucks betrifft.</p>
     *
     * <p>Gezaehlt werden nur Treffer aus genau dieser einen Regel. Ein Treffer aus
     * mehreren Regeln beweist nicht, dass die hier gesuchte an dieser Stelle wirkt.</p>
     */
    private Platzhalter platzhalterFuer(Misconception m, Aufgabe aufgabe, Platzhalter ersatz) {
        if (aufgabe == null) {
            return ersatz;
        }
        for (Expr knoten : vonInnenNachAussen(aufgabe.ausdruck())) {
            EvaluationResult richtig = auswerter.evaluate(knoten, aufgabe.kontext());
            if (!richtig.auswertbar()) {
                continue;
            }
            boolean regelWirktHier = simulator.simuliereAlle(knoten, aufgabe.kontext()).stream()
                    .filter(t -> t.bugIds().equals(List.of(m.id())))
                    .anyMatch(t -> !t.wert().mitTyp().equals(richtig.wert().mitTyp()));
            if (regelWirktHier) {
                Aufgabe teil = new Aufgabe(m.id(), knoten, aufgabe.kontext(), aufgabe.belegung());
                return new Platzhalter(teil, richtig);
            }
        }
        return ersatz;
    }

    /** Alle Teilausdruecke, die kleinsten zuerst, der ganze Ausdruck zuletzt. */
    private List<Expr> vonInnenNachAussen(Expr wurzel) {
        List<Expr> gesammelt = new ArrayList<>();
        sammle(wurzel, gesammelt);
        return gesammelt;
    }

    private void sammle(Expr e, List<Expr> ziel) {
        for (Expr kind : e.children()) {
            sammle(kind, ziel);
        }
        ziel.add(e);
    }

    /**
     * Model Tracing fuer unerklaerte Fehler: Es wird keine Kategorie genannt, sondern der
     * Loesungsweg schrittweise abgeglichen.
     */
    private Rueckmeldung modellabgleich(int fehlversuche, Platzhalter platzhalter, boolean auswertbar) {
        int stufe = begrenze(fehlversuche, 3);
        if (platzhalter == null) {
            return Rueckmeldung.modellabgleich(TRACING_OHNE_AUFGABE, stufe);
        }
        String text = switch (stufe) {
            case 0 -> TRACING_0;
            case 1 -> platzhalter.fuelle(TRACING_1_MIT_SCHRITT)
                    .or(() -> platzhalter.fuelle(TRACING_1_OHNE_SCHRITT))
                    .orElse(AUSWEICHTEXT);
            default -> auswertbar
                    ? platzhalter.fuelle(TRACING_2_AUSWERTBAR).orElse(AUSWEICHTEXT)
                    : platzhalter.fuelle(TRACING_2_NICHT_AUSWERTBAR).orElse(AUSWEICHTEXT);
        };
        return Rueckmeldung.modellabgleich(text, stufe);
    }

    private int begrenze(int fehlversuche, int anzahlStufen) {
        return Math.max(0, Math.min(fehlversuche, anzahlStufen - 1));
    }

    /**
     * Fuellt die Platzhalter eines Textes. Enthaelt der Text keine, bleibt er unveraendert.
     * Laesst sich einer nicht aufloesen, wird der Ausweichtext genommen, damit nie eine
     * Rueckmeldung mit sichtbaren geschweiften Klammern erscheint.
     */
    private String aufloesen(String text, Platzhalter platzhalter) {
        if (text.indexOf('{') < 0) {
            return text;
        }
        if (platzhalter == null) {
            return AUSWEICHTEXT;
        }
        Optional<String> gefuellt = platzhalter.fuelle(text);
        return gefuellt.orElse(AUSWEICHTEXT);
    }
}
