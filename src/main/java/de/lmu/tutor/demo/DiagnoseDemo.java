package de.lmu.tutor.demo;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.staticCall;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.var;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlerklassifikator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.feedback.Rueckmeldung;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Zeigt Schritt 5 und 6 im Zusammenspiel: zu einer Aufgabe wird eine typische
 * Fehleingabe diagnostiziert und daraus gestuftes Feedback erzeugt.
 * Ausfuehren mit:
 * <pre>  mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.DiagnoseDemo  </pre>
 */
public final class DiagnoseDemo {

    private static final StepEvaluator EVALUATOR = new StepEvaluator();
    private static final BugLibrary LIB = BugLibrary.loadDefault();
    private static final Fehlerklassifikator KLASSIFIKATOR = new Fehlerklassifikator(LIB);
    private static final Feedbackgenerator GENERATOR = new Feedbackgenerator();

    public static void main(String[] args) {
        System.out.println("Fehlerklassifikator und Feedback-Generator - Demo\n");

        EvaluationContext leer = new EvaluationContext();

        zeige("B01 Praezedenz", new Aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))), leer, ""),
                NutzerAntwort.wert(Value.ofInt(14)), 0);

        zeige("B02 Ganzzahldiv.", new Aufgabe("B02", bin("/", intLit(20), intLit(3)), leer, ""),
                NutzerAntwort.wert(Value.ofDouble(20 / 3.0)), 0);

        zeige("B04 Modulo", new Aufgabe("B04", bin("%", intLit(20), intLit(3)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(6)), 0);

        zeige("B07 nur Typ falsch", new Aufgabe("B02", bin("/", intLit(5), intLit(2)), leer, ""),
                NutzerAntwort.wert(Value.ofDouble(2.0)), 0);

        zeige("B08 nicht ausw.", new Aufgabe("B08", bin("+", intLit(5), boolLit(true)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(6)), 0);

        zeige("B10 Konkatenation", new Aufgabe("B10", bin("+", stringLit("17"), intLit(4)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(21)), 0);

        zeige("B13 char", new Aufgabe("B13", call(stringLit("Java"), "charAt", intLit(0)), leer, ""),
                NutzerAntwort.wert(Value.ofString("J")), 0);

        // Array-Aufgabe mit Belegung: a[k + 1] mit k = 1 -> "blau"; Fehler: Versatz ignoriert
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 1);
        zeige("B05 Array-Index", new Aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx,
                        "a = {rot, gelb, blau, gruen}, k = 1"),
                NutzerAntwort.wert(Value.ofString("gelb")), 0);

        // Klausur 1a) -- seit dem StaticCall-Knoten vom Evaluator abgedeckt.
        // Hinweis: In der aktuellen bug-library.json gibt es keine eigene Cast-Kategorie;
        // inhaltlich am naechsten liegt B03 (implizite Typkonversion). In Anhang A.1 der
        // Zulassungsarbeit traegt der Cast dagegen die Nummer B04 - die Nummerierung von
        // JSON und Text muss vor der Abgabe noch vereinheitlicht werden.
        EvaluationContext k1 = new EvaluationContext()
                .setzeStringArray("a", new String[]{"HEY", "x", "131", "2.1", "eip"});
        Expr klausur1a = cast(JType.INT, staticCall("Double", "parseDouble", index(var("a"), intLit(3))));
        zeige("Klausur 1a) Cast", new Aufgabe("B03", klausur1a, k1, "a = {HEY, x, 131, 2.1, eip}"),
                NutzerAntwort.wert(Value.ofDouble(2.1)), 0);

        // Scaffolding: dieselbe Fehlvorstellung dreimal hintereinander
        System.out.println("\n--- Scaffolding: dreimal derselbe Fehler (B01) ---");
        Aufgabe b01 = new Aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))), leer, "");
        for (int wiederholung = 0; wiederholung < 3; wiederholung++) {
            Rueckmeldung r = feedback(b01, NutzerAntwort.wert(Value.ofInt(14)), wiederholung);
            System.out.printf("  Stufe %d: %s%n", r.stufe(), r.text());
        }
    }

    private static void zeige(String titel, Aufgabe aufgabe, NutzerAntwort antwort, int wiederholungen) {
        EvaluationResult referenz = EVALUATOR.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
        Diagnose d = KLASSIFIKATOR.diagnostiziere(aufgabe, referenz, antwort);
        Rueckmeldung r = GENERATOR.erstelle(d, wiederholungen);

        System.out.println(titel + ":  " + aufgabe.render());
        if (!aufgabe.belegung().isBlank()) {
            System.out.println("  Belegung:  " + aufgabe.belegung());
        }
        System.out.println("  Referenz:  " + referenz.ergebnisText());
        System.out.println("  Eingabe:   " + (antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar"));
        System.out.println("  Diagnose:  " + d.misconception().map(m -> m.id() + " " + m.name()).orElse("keine")
                + "  [" + d.konfidenz() + "]");
        System.out.println("  Grund:     " + d.begruendung());
        System.out.println("  Feedback:  " + r.text());
        System.out.println();
    }

    private static Rueckmeldung feedback(Aufgabe aufgabe, NutzerAntwort antwort, int wiederholungen) {
        EvaluationResult referenz = EVALUATOR.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
        return GENERATOR.erstelle(KLASSIFIKATOR.diagnostiziere(aufgabe, referenz, antwort), wiederholungen);
    }
}