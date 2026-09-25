package de.lmu.tutor.feedback;

import java.util.Optional;
import java.util.Random;

import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Schritt 6 der Systemarchitektur: gibt kontextadaptives Feedback auf Basis des erkannten
 * Fehlertyps.
 *
 * <p>Setzt das Scaffolding nach Bruner (1960) um. Beim ersten Auftreten einer
 * Fehlvorstellung lenkt der Hinweis nur die Aufmerksamkeit, bei Wiederholung nennt er die
 * Java-Regel, danach rechnet er den Schritt konkret vor. Die Texte stehen in der Bug
 * Library, dieser Generator waehlt nur die passende Stufe aus.</p>
 *
 * <p>Wie oft eine Kategorie bei einer Lernenden schon aufgetreten ist, zaehlt der
 * Generator nicht selbst. Diese Zahl liefert das Studentenmodell. Die Trennung haelt den
 * Generator einzeln pruefbar.</p>
 *
 * <p><b>Platzhalter.</b> Die dritte Stufe soll am vorliegenden Ausdruck rechnen. Dafuer
 * enthalten die Texte Platzhalter, die {@link #erstelle(Diagnose, int, Aufgabe,
 * EvaluationResult)} gegen die tatsaechliche Aufgabe fuellt. Die aeltere Fassung ohne
 * Aufgabe bleibt erhalten, gibt bei einem Text mit Platzhaltern aber den allgemeinen
 * Ausweichtext aus, weil ihr die noetigen Angaben fehlen.</p>
 */
public final class Feedbackgenerator {

    private static final String[] LOB = {
            "Richtig!", "Genau so!", "Stimmt!", "Sehr gut, das passt!"
    };

    private static final String UNBEKANNTER_FEHLER =
            "Das ist leider nicht richtig. Schau dir den Ausdruck noch einmal Schritt fuer Schritt an "
                    + "und beginne beim am staerksten bindenden Operator.";

    /** Ausweichtext, wenn sich die Platzhalter der Stufe nicht aufloesen lassen. */
    private static final String AUSWEICHTEXT =
            "Werte den Ausdruck Schritt fuer Schritt von innen nach aussen aus und achte dabei "
                    + "auf die Datentypen der Teilergebnisse.";

    private final Random random;

    public Feedbackgenerator() {
        this(new Random());
    }

    public Feedbackgenerator(Random random) {
        this.random = random;
    }

    /**
     * Erstellt die Rueckmeldung mit Bezug auf die gestellte Aufgabe. Diese Fassung ist
     * der Normalfall, weil nur sie die Platzhalter der dritten Stufe fuellen kann.
     *
     * @param diagnose                      Ergebnis des Fehlerklassifikators
     * @param wiederholungenDieserKategorie wie oft diese Kategorie bei dieser Person schon
     *                                      aufgetreten ist, 0 beim ersten Mal
     * @param aufgabe                       die gestellte Aufgabe
     * @param referenz                      deren vorab berechnete Loesung
     */
    public Rueckmeldung erstelle(Diagnose diagnose, int wiederholungenDieserKategorie,
                                 Aufgabe aufgabe, EvaluationResult referenz) {
        return erstelle(diagnose, wiederholungenDieserKategorie,
                new Platzhalter(aufgabe, referenz));
    }

    /**
     * Erstellt die Rueckmeldung ohne Bezug auf eine Aufgabe. Texte mit Platzhaltern
     * werden dabei durch den allgemeinen Ausweichtext ersetzt.
     */
    public Rueckmeldung erstelle(Diagnose diagnose, int wiederholungenDieserKategorie) {
        return erstelle(diagnose, wiederholungenDieserKategorie, null);
    }

    private Rueckmeldung erstelle(Diagnose diagnose, int wiederholungen, Platzhalter platzhalter) {
        if (diagnose.korrekt()) {
            return Rueckmeldung.korrekt(LOB[random.nextInt(LOB.length)]);
        }
        if (diagnose.misconception().isEmpty()) {
            return Rueckmeldung.unbekannterFehler(UNBEKANNTER_FEHLER);
        }

        Misconception m = diagnose.misconception().get();
        int stufe = Math.max(0, Math.min(wiederholungen, m.anzahlFeedbackStufen() - 1));
        String text = aufloesen(m.feedbackStufe(stufe), platzhalter);

        if (diagnose.subtype().isPresent()) {
            Subtype s = diagnose.subtype().get();
            text += " (" + s.name() + ", z. B. " + s.beispiel() + ")";
        }
        return Rueckmeldung.fehler(text, m.id(), stufe);
    }

    /**
     * Letzte Scaffolding-Stufe unabhaengig von der Wiederholungszahl, etwa fuer einen
     * Knopf "Loesung zeigen".
     */
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