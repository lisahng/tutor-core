package de.lmu.tutor.session;

import java.util.List;

import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.EvaluationStep;
import de.lmu.tutor.feedback.Rueckmeldung;

/**
 * Was nach einem Loesungsversuch an die Oberflaeche zurueckgeht.
 *
 * <p>Die {@link Rueckmeldung} ist der Text fuer die Lernenden. Die {@link Diagnose} und
 * die Referenzloesung sind fuer die Anzeige nicht noetig, werden aber mitgegeben, damit
 * die Oberflaeche selbst entscheiden kann, wie viel sie zeigt. Die Zwischenschritte etwa
 * sollten erst nach einem gescheiterten Versuch sichtbar werden, nicht sofort.</p>
 *
 * @param korrekt     ob die Antwort richtig war
 * @param rueckmeldung der Text fuer die Lernenden samt Scaffolding-Stufe
 * @param diagnose    die erkannte Fehlvorstellung, bei korrekter Antwort ohne Kategorie
 * @param referenz    die vorab berechnete Musterloesung samt Zwischenschritten
 * @param versuch     der wievielte Versuch an dieser Aufgabe das war (beginnend bei 1)
 */
public record Antwortergebnis(
        boolean korrekt,
        Rueckmeldung rueckmeldung,
        Diagnose diagnose,
        EvaluationResult referenz,
        int versuch
) {

    /** Der Feedback-Text fuer die Lernenden. */
    public String text() {
        return rueckmeldung.text();
    }

    /** Die Musterloesung als Text, etwa "11 : int" oder "nicht auswertbar". */
    public String referenzText() {
        return referenz.ergebnisText();
    }

    /** Die protokollierten Zwischenschritte der korrekten Auswertung. */
    public List<EvaluationStep> zwischenschritte() {
        return referenz.schritte();
    }
}