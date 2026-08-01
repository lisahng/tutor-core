package de.lmu.tutor.demo;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.EvaluationStep;
import de.lmu.tutor.eval.StepEvaluator;

/**
 * Zeigt Schritt 3: wertet Ausdruecke aus und druckt Wert, Datentyp und Schrittprotokoll.
 * Ausfuehren mit:
 * <pre>  mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.EvaluatorDemo  </pre>
 */
public final class EvaluatorDemo {

    public static void main(String[] args) {
        StepEvaluator evaluator = new StepEvaluator();

        System.out.println("Schritt-Evaluator - Demo \n");

        // Hauptbeispiel: a[k + 1].length() * k  mit a = {rot,gelb,blau,gruen}, k = 2  -> 10 : int
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 2);
        Expr haupt = bin("*",
                call(index(var("a"), bin("+", var("k"), intLit(1))), "length"),
                var("k"));

        System.out.println("Ausdruck: " + haupt.render());
        System.out.println("Belegung: a = {rot, gelb, blau, gruen}, k = 2\n");
        EvaluationResult r = evaluator.evaluate(haupt, ctx);
        System.out.println("Schrittprotokoll (von innen nach aussen):");
        int nr = 1;
        for (EvaluationStep s : r.schritte()) {
            System.out.printf("  %d. %-24s =>  %s%n", nr++, s.teilausdruck(), s.ergebnis());
        }
        System.out.println("\nErgebnis: " + r.ergebnisText());

        System.out.println("\n--- Weitere Beispiele ---");
        EvaluationContext leer = new EvaluationContext();
        zeige(evaluator, leer, "B01 Praezedenz", bin("+", intLit(3), bin("*", intLit(4), intLit(2))));
        zeige(evaluator, leer, "B02 Ganzzahldiv.", bin("/", intLit(20), intLit(3)));
        zeige(evaluator, leer, "B04 Cast", cast(JType.INT, doubleLit(2.1)));
        zeige(evaluator, leer, "B10 Modulo", bin("%", intLit(20), intLit(3)));
        zeige(evaluator, leer, "B03 Konkatenation", bin("+", stringLit("17"), intLit(4)));
        zeige(evaluator, leer, "B13 charAt", call(stringLit("Java"), "charAt", intLit(0)));
        zeige(evaluator, leer, "B06 Chaining", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase"));
        zeige(evaluator, leer, "B08 nicht ausw.", bin("+", intLit(5), boolLit(true)));
        zeige(evaluator, leer, "B09 Kurzschluss", bin("&&", boolLit(false),
                bin("==", bin("/", intLit(1), intLit(0)), intLit(0))));
    }

    private static void zeige(StepEvaluator ev, EvaluationContext ctx, String titel, Expr e) {
        EvaluationResult r = ev.evaluate(e, ctx);
        System.out.printf("  %-18s %-24s ->  %s%n", titel, e.render(), r.ergebnisText());
    }
}