package de.lmu.tutor.gen;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.eval.EvaluationContext;

/**
 * Eine generierte Aufgabe: der Ausdruck, seine Variablenbelegung und die
 * Bug-Kategorie, fuer die er erzeugt wurde. Die Referenzloesung berechnet
 * anschliessend der {@code StepEvaluator}.
 *
 * @param kategorieId die Bug-Kategorie, z. B. "B05"
 * @param ausdruck    der erzeugte Ausdrucksbaum
 * @param kontext     die Variablenbelegung fuer die Auswertung
 * @param belegung    menschenlesbare Belegung, z. B. {@code a = {...}, k = 2} (leer, wenn keine Variablen)
 */
public record Aufgabe(String kategorieId, Expr ausdruck, EvaluationContext kontext, String belegung) {

    /** Der Ausdruck als Java-Quelltext. */
    public String render() {
        return ausdruck.render();
    }
}