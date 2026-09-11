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
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlerklassifikator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Zeigt Schritt 5 (Vergleichsmodul und Fehlerklassifikator): Zu einer Aufgabe wird
 * eine typische Fehleingabe diagnostiziert und die erkannte Bug-Kategorie ausgegeben.
 *
 * <p>Diese Fassung kommt ohne das Package {@code de.lmu.tutor.feedback} (Schritt 6)
 * aus. Statt einer generierten Rueckmeldung werden die in der Bug Library
 * hinterlegten Scaffolding-Stufen direkt angezeigt. Sobald Schritt 6 eingebaut ist,
 * uebernimmt der Feedbackgenerator die Auswahl der passenden Stufe.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn clean compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.DiagnoseDemo"  </pre>
 */
public final class DiagnoseDemo {

    private static final StepEvaluator EVALUATOR = new StepEvaluator();
    private static final BugLibrary LIB = BugLibrary.loadDefault();
    private static final Fehlerklassifikator KLASSIFIKATOR = new Fehlerklassifikator(LIB);

    public static void main(String[] args) {
        System.out.println("Fehlerklassifikator - Demo (Schritt 5)\n");

        EvaluationContext leer = new EvaluationContext();

        zeige("B01 Praezedenz", new Aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))), leer, ""),
                NutzerAntwort.wert(Value.ofInt(14)));

        zeige("B02 Ganzzahldiv.", new Aufgabe("B02", bin("/", intLit(20), intLit(3)), leer, ""),
                NutzerAntwort.wert(Value.ofDouble(20 / 3.0)));

        zeige("B04 Modulo", new Aufgabe("B04", bin("%", intLit(20), intLit(3)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(6)));

        zeige("B07 nur Typ falsch", new Aufgabe("B02", bin("/", intLit(5), intLit(2)), leer, ""),
                NutzerAntwort.wert(Value.ofDouble(2.0)));

        zeige("B08 nicht ausw.", new Aufgabe("B08", bin("+", intLit(5), boolLit(true)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(6)));

        zeige("B10 Konkatenation", new Aufgabe("B10", bin("+", stringLit("17"), intLit(4)), leer, ""),
                NutzerAntwort.wert(Value.ofInt(21)));

        zeige("B13 char", new Aufgabe("B13", call(stringLit("Java"), "charAt", intLit(0)), leer, ""),
                NutzerAntwort.wert(Value.ofString("J")));

        // Array-Aufgabe mit Belegung: a[k + 1] mit k = 1 -> "blau"; Fehler: Versatz ignoriert
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 1);
        zeige("B05 Array-Index", new Aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx,
                        "a = {rot, gelb, blau, gruen}, k = 1"),
                NutzerAntwort.wert(Value.ofString("gelb")));

        // Klausur 1a) -- seit dem StaticCall-Knoten vom Evaluator abgedeckt.
        // Hinweis: In der aktuellen bug-library.json gibt es keine eigene Cast-Kategorie;
        // inhaltlich am naechsten liegt B03 (implizite Typkonversion).
        EvaluationContext k1 = new EvaluationContext()
                .setzeStringArray("a", new String[]{"HEY", "x", "131", "2.1", "eip"});
        Expr klausur1a = cast(JType.INT, staticCall("Double", "parseDouble", index(var("a"), intLit(3))));
        zeige("Klausur 1a) Cast", new Aufgabe("B03", klausur1a, k1, "a = {HEY, x, 131, 2.1, eip}"),
                NutzerAntwort.wert(Value.ofDouble(2.1)));

        // Korrekte Antwort zum Vergleich
        zeige("Korrekte Antwort", new Aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))), leer, ""),
                NutzerAntwort.wert(Value.ofInt(11)));
    }

    private static void zeige(String titel, Aufgabe aufgabe, NutzerAntwort antwort) {
        EvaluationResult referenz = EVALUATOR.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
        Diagnose d = KLASSIFIKATOR.diagnostiziere(aufgabe, referenz, antwort);

        System.out.println(titel + ":  " + aufgabe.render());
        if (!aufgabe.belegung().isBlank()) {
            System.out.println("  Belegung:  " + aufgabe.belegung());
        }
        System.out.println("  Referenz:  " + referenz.ergebnisText());
        System.out.println("  Eingabe:   " + (antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar"));

        if (d.korrekt()) {
            System.out.println("  Diagnose:  korrekt");
            System.out.println();
            return;
        }

        System.out.println("  Diagnose:  " + d.misconception().map(m -> m.id() + " " + m.name()).orElse("keine")
                + "  [" + d.konfidenz() + "]");
        System.out.println("  Grund:     " + d.begruendung());

        // Solange Schritt 6 fehlt: die hinterlegten Scaffolding-Stufen direkt anzeigen.
        if (d.misconception().isPresent()) {
            Misconception m = d.misconception().get();
            for (int stufe = 0; stufe < m.anzahlFeedbackStufen(); stufe++) {
                System.out.printf("  Stufe %d:   %s%n", stufe, m.feedbackStufe(stufe));
            }
        }
        System.out.println();
    }
}