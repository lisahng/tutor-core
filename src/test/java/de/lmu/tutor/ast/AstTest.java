package de.lmu.tutor.ast;

import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer Schritt 2: den Ausdrucksbaum (AST).
 * Geprueft werden vor allem das render() (inkl. Klammersetzung) und die Struktur.
 */
class AstTest {

    @Test
    void renderRespektiertPraezedenz() {
        // 3 + 4 * 2 : keine Klammern noetig
        Expr e = bin("+", intLit(3), bin("*", intLit(4), intLit(2)));
        assertEquals("3 + 4 * 2", e.render());
    }

    @Test
    void renderSetztKlammernWennNoetig() {
        // (3 + 4) * 2 : Klammern noetig, weil + schwaecher bindet als *
        Expr e = bin("*", bin("+", intLit(3), intLit(4)), intLit(2));
        assertEquals("(3 + 4) * 2", e.render());
    }

    @Test
    void renderLinksAssoziativOhneKlammern() {
        // (a - b) - c wird als a - b - c gerendert
        Expr e = bin("-", bin("-", var("a"), var("b")), var("c"));
        assertEquals("a - b - c", e.render());
    }

    @Test
    void renderRechtsGeklammert() {
        // a - (b - c) behaelt die Klammern
        Expr e = bin("-", var("a"), bin("-", var("b"), var("c")));
        assertEquals("a - (b - c)", e.render());
    }

    @Test
    void renderHauptbeispiel() {
        Expr e = bin("*",
                call(index(var("a"), bin("+", var("k"), intLit(1))), "length"),
                var("k"));
        assertEquals("a[k + 1].length() * k", e.render());
        assertEquals(8, e.groesse());
        assertEquals(5, e.hoehe());
    }

    @Test
    void renderMethodChaining() {
        Expr e = call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase");
        assertEquals("\"HEY\".substring(1).toLowerCase()", e.render());
    }

    @Test
    void renderNegationMitKlammer() {
        Expr e = not(bin("==", var("x"), intLit(0)));
        assertEquals("!(x == 0)", e.render());
    }

    @Test
    void renderCast() {
        assertEquals("(int) 2.1", cast(JType.INT, doubleLit(2.1)).render());
    }

    @Test
    void renderLiterale() {
        assertEquals("\"k2\"", stringLit("k2").render());
        assertEquals("'J'", charLit('J').render());
        assertEquals("2.0", doubleLit(2.0).render());
        assertEquals("true", boolLit(true).render());
        assertEquals("42", intLit(42).render());
    }

    @Test
    void renderInkrement() {
        assertEquals("++b", preInc(var("b")).render());
        assertEquals("b++", postInc(var("b")).render());
    }

    @Test
    void renderTernaer() {
        Expr e = ternary(bin(">", var("b"), intLit(0)), var("b"), neg(var("b")));
        assertEquals("b > 0 ? b : -b", e.render());
    }

    @Test
    void kinderGroesseHoehe() {
        Expr plus = bin("+", var("k"), intLit(1));
        assertEquals(2, plus.children().size());
        assertEquals(3, plus.groesse());
        assertEquals(2, plus.hoehe());
        assertTrue(plus.children().get(0) instanceof Expr.Var);
        assertTrue(plus.children().get(1) instanceof Expr.Lit);
    }
}
