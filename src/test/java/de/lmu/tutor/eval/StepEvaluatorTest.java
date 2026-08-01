package de.lmu.tutor.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.neg;
import static de.lmu.tutor.ast.AST.postInc;
import static de.lmu.tutor.ast.AST.preInc;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;

/** Tests fuer Schritt 3: den Schritt-Evaluator. Deckt die Bug-Kategorien B01-B14 ab. */
class StepEvaluatorTest {

    private final StepEvaluator ev = new StepEvaluator();

    private EvaluationResult aus(Expr e, EvaluationContext ctx) { return ev.evaluate(e, ctx); }
    private EvaluationResult aus(Expr e) { return ev.evaluate(e, new EvaluationContext()); }

    @Test
    void hauptbeispiel() {
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 2);
        Expr e = bin("*", call(index(var("a"), bin("+", var("k"), intLit(1))), "length"), var("k"));
        EvaluationResult r = aus(e, ctx);
        assertTrue(r.auswertbar());
        assertEquals("10", r.wert().render());
        assertEquals(JType.INT, r.wert().typ());
        assertEquals(4, r.schritte().size());  // k+1, a[..], .length(), * k
    }

    @Test void b01_praezedenz() { assertErgebnis("11", JType.INT, bin("+", intLit(3), bin("*", intLit(4), intLit(2)))); }
    @Test void b02_ganzzahldivision() { assertErgebnis("6", JType.INT, bin("/", intLit(20), intLit(3))); }
    @Test void b04_castTruncation() { assertErgebnis("2", JType.INT, cast(JType.INT, doubleLit(2.1))); }
    @Test void b10_modulo() { assertErgebnis("2", JType.INT, bin("%", intLit(20), intLit(3))); }
    @Test void b03_konkatenation() { assertErgebnis("\"174\"", JType.STRING, bin("+", stringLit("17"), intLit(4))); }
    @Test void b13_charAt() { assertErgebnis("'J'", JType.CHAR, call(stringLit("Java"), "charAt", intLit(0))); }
    @Test void b06_chaining() { assertErgebnis("\"ey\"", JType.STRING, call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase")); }

    @Test void b07_datentyp() { // 5 / 2 ist int, nicht double
        EvaluationResult r = aus(bin("/", intLit(5), intLit(2)));
        assertEquals(JType.INT, r.wert().typ());
        assertEquals("2", r.wert().render());
    }

    @Test void b08_nichtAuswertbar() { assertNichtAuswertbar(bin("+", intLit(5), boolLit(true))); }
    @Test void b08b_unbekannteVariable() { assertNichtAuswertbar(bin("+", var("x"), intLit(1))); }
    @Test void b05a_indexAusserhalb() {
        EvaluationContext ctx = new EvaluationContext().setzeStringArray("a", new String[]{"HI", "x", "131", "2.1", "eip"});
        assertFalse(aus(index(var("a"), intLit(5)), ctx).auswertbar());
    }

    @Test void b09_kurzschluss() { // false && (1/0==0) -> false, KEINE Division durch Null
        EvaluationResult r = aus(bin("&&", boolLit(false), bin("==", bin("/", intLit(1), intLit(0)), intLit(0))));
        assertTrue(r.auswertbar());
        assertEquals("false", r.wert().render());
    }

    @Test void b11_ternaer() { // (b>0)?b:-b mit b=-3 -> 3
        EvaluationContext ctx = new EvaluationContext().setzeInt("b", -3);
        assertErgebnis("3", JType.INT, ternary(bin(">", var("b"), intLit(0)), var("b"), neg(var("b"))), ctx);
    }

    @Test void b12_praefixInkrement() { // ++b mit b=2 -> 3
        EvaluationContext ctx = new EvaluationContext().setzeInt("b", 2);
        assertErgebnis("3", JType.INT, preInc(var("b")), ctx);
    }
    @Test void b12_postfixInkrement() { // b++ mit b=2 -> liefert 2 (alter Wert)
        EvaluationContext ctx = new EvaluationContext().setzeInt("b", 2);
        assertErgebnis("2", JType.INT, postInc(var("b")), ctx);
    }

    @Test void b14_equals() { assertErgebnis("true", JType.BOOL, call(stringLit("a"), "equals", stringLit("a"))); }

    // ---- Hilfsmethoden ----
    private void assertErgebnis(String wert, JType typ, Expr e) { assertErgebnis(wert, typ, e, new EvaluationContext()); }
    private void assertErgebnis(String wert, JType typ, Expr e, EvaluationContext ctx) {
        EvaluationResult r = aus(e, ctx);
        assertTrue(r.auswertbar(), "sollte auswertbar sein: " + e.render());
        assertEquals(wert, r.wert().render());
        assertEquals(typ, r.wert().typ());
    }
    private void assertNichtAuswertbar(Expr e) { assertFalse(aus(e).auswertbar(), "sollte nicht auswertbar sein: " + e.render()); }
}