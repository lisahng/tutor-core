package de.lmu.tutor.feedback;

import java.util.Random;

import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;
import de.lmu.tutor.diagnose.Diagnose;

/**
 * Schritt 6 der Systemarchitektur (Tabelle 4): "Gibt kontextadaptives Feedback
 * basierend auf dem erkannten Fehlertyp (Scaffolding)".
 *
 * <p>Setzt das in 3.2.1 beschriebene Scaffolding-Prinzip nach Bruner (1960) um: Beim
 * ersten Auftreten einer Fehlvorstellung gibt es einen allgemeinen Hinweis (Stufe 0),
 * bei Wiederholung einen spezifischeren (Stufe 1), danach eine direkte Loesungshilfe
 * (Stufe 2). Die Stufe selbst wird nicht hier verwaltet - wie oft eine Kategorie bei
 * einer Lernenden schon aufgetreten ist, liefert das (spaetere) Studentenmodell; der
 * Feedback-Generator nimmt diese Zahl nur entgegen und waehlt die passende, in der Bug
 * Library hinterlegte Stufe aus ({@link Misconception#feedbackStufe(int)}).</p>
 *
 * <p>Ist keine konkrete Kategorie diagnostiziert (Diagnose "unbekannt", vgl.
 * {@link de.lmu.tutor.diagnose.Fehlerklassifikator}), wird ein allgemeiner Hinweis ausgegeben - passend zur
 * in Besprechung 1 besprochenen Idee, solche Faelle separat zu vermerken
 * ("Kommentarspalte"), statt eine falsche Kategorie zu behaupten.</p>
 */
public final class Feedbackgenerator {

    private static final String[] LOB = {
            "Richtig!", "Genau so!", "Stimmt!", "Sehr gut, das passt!"
    };

    private static final String UNBEKANNTER_FEHLER = "Das ist leider nicht richtig. Schauen Sie sich den Ausdruck "
            + "noch einmal Schritt fuer Schritt an - beginnen Sie beim am staerksten bindenden Operator.";

    private final Random random;

    public Feedbackgenerator() {
        this(new Random());
    }

    public Feedbackgenerator(Random random) {
        this.random = random;
    }

    /**
     * Erstellt die Rueckmeldung zu einer Diagnose.
     *
     * @param diagnose                  Ergebnis des Fehlerklassifikators
     * @param wiederholungenDieserKategorie wie oft diese Bug-Kategorie bei dieser Person schon
     *                                       aufgetreten ist (0 = erstes Mal); steuert die Scaffolding-Stufe
     */
    public Rueckmeldung erstelle(Diagnose diagnose, int wiederholungenDieserKategorie) {
        if (diagnose.korrekt()) {
            return Rueckmeldung.korrekt(LOB[random.nextInt(LOB.length)]);
        }
        if (diagnose.misconception().isEmpty()) {
            return Rueckmeldung.unbekannterFehler(UNBEKANNTER_FEHLER);
        }

        Misconception m = diagnose.misconception().get();
        int stufe = Math.max(0, Math.min(wiederholungenDieserKategorie, m.anzahlFeedbackStufen() - 1));
        String text = m.feedbackStufe(stufe);

        if (diagnose.subtype().isPresent()) {
            Subtype s = diagnose.subtype().get();
            text += " (" + s.name() + ", z. B. " + s.beispiel() + ")";
        }
        return Rueckmeldung.fehler(text, m.id(), stufe);
    }

    /**
     * Letzte Scaffolding-Stufe (direkte Loesungshilfe) fuer eine Kategorie, unabhaengig
     * von der Wiederholungszahl - nuetzlich, wenn explizit "Loesung zeigen" angefragt wird.
     */
    public Rueckmeldung erstelleLoesungshilfe(Misconception m) {
        return Rueckmeldung.fehler(m.feedbackStufe(m.anzahlFeedbackStufen() - 1), m.id(), m.anzahlFeedbackStufen() - 1);
    }
}