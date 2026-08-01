package de.lmu.tutor.eval;

/**
 * Wird intern geworfen, sobald ein Ausdruck nicht auswertbar ist
 * (Typfehler, unbekannte Variable, Division durch Null, Index ausserhalb der Grenzen).
 * Der {@link StepEvaluator} faengt sie und macht daraus ein Ergebnis "nicht auswertbar".
 */
public class NotEvaluableException extends RuntimeException {
    public NotEvaluableException(String grund) {
        super(grund);
    }
}