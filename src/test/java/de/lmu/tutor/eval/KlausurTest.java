package de.lmu.tutor.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.charLit;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.not;
import static de.lmu.tutor.ast.AST.postDec;
import static de.lmu.tutor.ast.AST.postInc;
import static de.lmu.tutor.ast.AST.preInc;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;

/**
 * Absicherung des Evaluators mit den sechs analysierten Klausuren aus Anhang A.2
 * der Zulassungsarbeit. Jede Teilaufgabe wird gegen ihre Musterloesung (Wert und
 * Datentyp bzw. "nicht auswertbar") geprueft.
 *
 * <p>Nicht enthalten (ausserhalb des aktuellen AST-Umfangs): Klausur 1a
 * (Double.parseDouble, statischer Methodenaufruf) und Klausur 4c (new String,
 * Objekterzeugung). Diese lassen sich ergaenzen, sobald der Baum Knoten fuer
 * statische Aufrufe bzw. Objekterzeugung erhaelt.</p>
 */
class KlausurTest {

    private final StepEvaluator ev = new StepEvaluator();

    // ---- Belegungen der sechs Klausuren (vor jeder Teilaufgabe frisch) ----
    private EvaluationContext k1() {
        return new EvaluationContext()
                .setzeStringArray("a", new String[]{"HEY", "x", "131", "2.1", "eip"})
                .setzeInt("b", 2).setzeInt("k", 1).setzeInt("i", 5);
    }
    private EvaluationContext k2() {
        return new EvaluationContext()
                .setzeStringArray("a", new String[]{"HI", "x", "131", "2.1", "eip"})
                .setzeInt("b", 2).setzeInt("k", 1).setzeInt("i", 5);
    }
    private EvaluationContext k3() {
        return new EvaluationContext()
                .setzeIntArray("a", new int[]{1, 2, 3}).setzeInt("i", 2);
    }
    private EvaluationContext k4() {
        return new EvaluationContext()
                .setzeStringArray("s", new String[]{"a", "bc", "def"}).setzeInt("i", 2);
    }
    private EvaluationContext k5() {
        return new EvaluationContext()
                .setzeStringArray("s", new String[]{"a", "2.0", "3", "xyz"}).setzeInt("i", 2);
    }
    private EvaluationContext k6() {
        return new EvaluationContext().setzeInt("x", 2).setzeInt("y", 5);
    }

    // ================= Klausur 1 =================
    @Test void k1b() { wert("3", JType.INT, bin("*", call(index(var("a"), bin("+", var("k"), intLit(1))), "length"), var("k")), k1()); }
    @Test void k1c() { wert("\"ey\"", JType.STRING, call(call(index(var("a"), intLit(0)), "substring", intLit(1)), "toLowerCase"), k1()); }
    @Test void k1d() { nichtAuswertbar(bin("-", bin("+", stringLit("17"), intLit(4)), var("k")), k1()); }
    @Test void k1e() { wert("12", JType.INT, bin("+", bin("+", bin("%", var("k"), intLit(3)), var("i")), bin("/", intLit(20), intLit(3))), k1()); }
    @Test void k1f() { wert("true", JType.BOOL,
            ternary(not(bin("!=", var("b"), preInc(var("i")))), bin("==", var("b"), var("i")), bin("==", var("b"), postInc(var("b")))), k1()); }

    // ================= Klausur 2 =================
    @Test void k2a() { nichtAuswertbar(cast(JType.INT, index(var("a"), intLit(2))), k2()); }
    @Test void k2b() { wert("'e'", JType.CHAR, call(index(var("a"), bin("-", call(var("a"), "length"), intLit(1))), "charAt", intLit(0)), k2()); }
    @Test void k2c() { wert("\"IP\"", JType.STRING, call(call(index(var("a"), intLit(4)), "substring", intLit(1)), "toUpperCase"), k2()); }
    @Test void k2d() { wert("\"k2\"", JType.STRING, bin("+", stringLit("k"), var("b")), k2()); }
    @Test void k2e() { wert("11", JType.INT, bin("+", bin("+", bin("%", var("k"), intLit(7)), var("i")), bin("/", intLit(21), intLit(4))), k2()); }
    @Test void k2f() { wert("false", JType.BOOL,
            ternary(bin("!=", var("b"), preInc(var("i"))), bin("==", var("k"), postDec(var("b"))), bin("==", var("b"), postInc(var("b")))), k2()); }

    // ================= Klausur 3 =================
    @Test void k3a() { wert("3", JType.INT, index(var("a"), var("i")), k3()); }
    @Test void k3b() { nichtAuswertbar(bin("==", var("a"), var("i")), k3()); }
    @Test void k3c() { nichtAuswertbar(index(var("a"), call(var("a"), "length")), k3()); }
    @Test void k3d() { wert("true", JType.BOOL, bin("==", index(var("a"), intLit(2)), bin("+", var("i"), intLit(1))), k3()); }
    @Test void k3e() { wert("3", JType.INT, index(var("a"), bin("-", call(var("a"), "length"), intLit(1))), k3()); }

    // ================= Klausur 4 =================
    @Test void k4a() { wert("true", JType.BOOL, bin("==", bin("+", var("i"), intLit(1)), call(var("s"), "length")), k4()); }
    @Test void k4b() { nichtAuswertbar(bin("!=", bin("+", stringLit("Jein"), var("i")), var("i")), k4()); }
    @Test void k4d() { nichtAuswertbar(bin("==", index(index(var("s"), intLit(1)), intLit(2)), charLit('c')), k4()); }

    // ================= Klausur 5 =================
    @Test void k5a() { nichtAuswertbar(bin("==", bin("+", index(var("s"), intLit(3)), intLit(1)), postInc(var("i"))), k5()); }
    @Test void k5b() { wert("3", JType.INT, bin("+", cast(JType.INT, bin("/", var("i"), doubleLit(8.0))), intLit(3)), k5()); }
    @Test void k5c() { wert("true", JType.BOOL, call(index(var("s"), bin("-", call(var("s"), "length"), intLit(4))), "equals", stringLit("a")), k5()); }

    // ================= Klausur 6 (Single-Choice) =================
    @Test void k6_3() { wert("\"210\"", JType.STRING, bin("+", bin("+", var("x"), stringLit("")), bin("*", intLit(2), intLit(5))), k6()); }
    @Test void k6_4() { wert("20", JType.INT, ternary(bin(">", preInc(var("x")), intLit(2)), bin("*", intLit(4), var("y")), bin("*", intLit(2), var("y"))), k6()); }
    @Test void k6_5() { wert("7", JType.INT, bin("/", bin("*", var("y"), intLit(3)), var("x")), k6()); }

    // ---- Hilfsmethoden ----
    private void wert(String erwartet, JType typ, Expr e, EvaluationContext ctx) {
        EvaluationResult r = ev.evaluate(e, ctx);
        assertTrue(r.auswertbar(), e.render() + " sollte auswertbar sein, war: " + r.ergebnisText());
        assertEquals(erwartet, r.wert().render(), "Wert von " + e.render());
        assertEquals(typ, r.wert().typ(), "Typ von " + e.render());
    }
    private void nichtAuswertbar(Expr e, EvaluationContext ctx) {
        assertFalse(ev.evaluate(e, ctx).auswertbar(), e.render() + " sollte nicht auswertbar sein");
    }
}