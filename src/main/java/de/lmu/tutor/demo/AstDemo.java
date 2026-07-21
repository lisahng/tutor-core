package de.lmu.tutor.demo;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;

import static de.lmu.tutor.ast.AST.*;

/**
 * Zeigt Schritt 2: baut Ausdrucksbaeume, gibt sie als Java-Quelltext aus
 * und druckt ihre Struktur. Ausfuehren mit:
 * <pre>  mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.AstDemo  </pre>
 */
public final class AstDemo {

    public static void main(String[] args) {
        System.out.println("=== Ausdrucksbaum (AST) - Demo ===\n");

        // Hauptbeispiel: a[k + 1].length() * k
        Expr beispiel = bin("*",
                call(index(var("a"), bin("+", var("k"), intLit(1))), "length"),
                var("k"));

        zeige("Hauptbeispiel", beispiel);
        System.out.println("\n  Struktur des Hauptbeispiels (von der Wurzel nach unten):");
        druckeBaum(beispiel, "    ");
        System.out.println();

        // Weitere Beispiele aus Bug Library / Klausuren
        System.out.println("  Weitere Ausdruecke:");
        zeige("B01 Praezedenz", bin("+", intLit(3), bin("*", intLit(4), intLit(2))));
        zeige("  mit Klammern", bin("*", bin("+", intLit(3), intLit(4)), intLit(2)));
        zeige("B04 Cast", cast(JType.INT, doubleLit(2.1)));
        zeige("B06 Chaining", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase"));
        zeige("B08 nicht ausw.", bin("+", intLit(5), boolLit(true)));
        zeige("B09 Kurzschluss", bin("&&", boolLit(false),
                bin("==", bin("/", intLit(1), intLit(0)), intLit(0))));
        zeige("B11 Ternaer", ternary(bin(">", var("b"), intLit(0)), var("b"), neg(var("b"))));
        zeige("B12 Inkrement", preInc(var("b")));
        zeige("B13 charAt", call(stringLit("Java"), "charAt", intLit(0)));
    }

    /** Gibt Titel, gerenderten Ausdruck und Kennzahlen aus. */
    private static void zeige(String titel, Expr e) {
        System.out.printf("  %-18s %-34s (Knoten: %d, Hoehe: %d)%n",
                titel, e.render(), e.groesse(), e.hoehe());
    }

    /** Druckt den Baum eingerueckt, ein Knoten pro Zeile. */
    private static void druckeBaum(Expr e, String einzug) {
        System.out.println(einzug + knotenName(e));
        for (Expr kind : e.children()) {
            druckeBaum(kind, einzug + "  ");
        }
    }

    /** Kurzer Name eines Knotens fuer die Baumdarstellung. */
    private static String knotenName(Expr e) {
        if (e instanceof Expr.Lit l) {
            return "Lit " + l.render();
        } else if (e instanceof Expr.Var v) {
            return "Var " + v.name();
        } else if (e instanceof Expr.Bin b) {
            return "Bin " + b.op();
        } else if (e instanceof Expr.Unary u) {
            return "Unary " + u.op();
        } else if (e instanceof Expr.Cast c) {
            return "Cast (" + c.zielTyp().javaName() + ")";
        } else if (e instanceof Expr.Index) {
            return "Index [ ]";
        } else if (e instanceof Expr.Call c) {
            return "Call ." + c.methode() + "()";
        } else if (e instanceof Expr.Ternary) {
            return "Ternary ? :";
        } else if (e instanceof Expr.IncDec d) {
            return "IncDec " + d.op() + (d.prefix() ? " (prefix)" : " (postfix)");
        }
        return "";
    }
}
