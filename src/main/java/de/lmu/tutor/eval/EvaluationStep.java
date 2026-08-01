package de.lmu.tutor.eval;

/**
 * Ein einzelner Auswertungsschritt fuer das Schrittprotokoll,
 * z. B. Teilausdruck "k + 1" ergibt "3 : int".
 */
public record EvaluationStep(String teilausdruck, String ergebnis) {
    @Override
    public String toString() {
        return teilausdruck + "  =>  " + ergebnis;
    }
}