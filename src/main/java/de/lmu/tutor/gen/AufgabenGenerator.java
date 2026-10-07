package de.lmu.tutor.gen;

import java.util.Random;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.postInc;
import static de.lmu.tutor.ast.AST.preInc;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.eval.EvaluationContext;

/**
 * Erzeugt zu einer Bug-Kategorie (B01-B14) einen zufaelligen, typkonformen
 * Ausdruck mit gueltiger Variablenbelegung. Die erzeugten Aufgaben sind so
 * gebaut, dass sie der {@code StepEvaluator} eindeutig loesen kann -- ausser
 * Kategorie B08, die absichtlich nicht auswertbar ist.
 *
 * <p>Mit einem festen Seed ({@link #AufgabenGenerator(long)}) sind die Aufgaben
 * reproduzierbar -- praktisch fuer Tests.</p>
 */
public final class AufgabenGenerator {

    private static final String[] WOERTER = {"rot", "gelb", "blau", "gruen", "Java", "Test", "Hallo", "Welt"};

    private final Random random;

    public AufgabenGenerator() { this(new Random()); }
    public AufgabenGenerator(long seed) { this(new Random(seed)); }
    public AufgabenGenerator(Random random) { this.random = random; }

    /** Erzeugt eine Aufgabe fuer die gegebene Kategorie (z. B. "B05"). */
    public Aufgabe generiere(String kategorieId) {
        return switch (kategorieId) {
            case "B01" -> b01();
            case "B02" -> b02();
            case "B03" -> b03();
            case "B04" -> b04();
            case "B05" -> b05();
            case "B06" -> b06();
            case "B07" -> b07();
            case "B08" -> b08();
            case "B09" -> b09();
            case "B10" -> b10();
            case "B11" -> b11();
            case "B12" -> b12();
            case "B13" -> b13();
            case "B14" -> b14();
            default -> throw new IllegalArgumentException("Keine Vorlage fuer Kategorie: " + kategorieId);
        };
    }

    // ---- Vorlagen je Kategorie ----

    /**
     * Operatorpraezedenz. Drei Formen wechseln sich ab:
     * {@code x + y * z}, {@code x + y / z} und {@code x + y % z}.
     *
     * <p>Die beiden letzten Formen sind noetig, damit <em>Fehlerkombinationen</em>
     * ueberhaupt auftreten koennen. Wer die Praezedenz missachtet, wendet beim Rechnen von
     * links nach rechts zugleich seine eigene Vorstellung von Division oder Modulo an. Bei
     * {@code 12 + 9 / 2} etwa ergibt die korrekte Auswertung 16, links nach rechts
     * gerechnet 10, und mit zusaetzlich angenommener Fliesskommadivision 10.5. Nur der
     * letzte Wert laesst sich allein durch zwei gleichzeitige Fehlvorstellungen erklaeren.</p>
     *
     * <p>Mit der reinen Form {@code x + y * z} gaebe es keinen solchen Fall, denn ohne
     * Division oder Modulo wirkt sich nur die Reihenfolge aus.</p>
     */
    private Aufgabe b01() {
        return switch (random.nextInt(3)) {
            case 1 -> b01MitDivision();
            case 2 -> b01MitModulo();
            default -> ohneVars("B01",
                    bin("+", intLit(rnd(2, 9)), bin("*", intLit(rnd(2, 9)), intLit(rnd(2, 9)))));
        };
    }

    /**
     * {@code x + y / z}, so gewaehlt, dass sich drei Ergebnisse unterscheiden: die korrekte
     * Loesung, die Links-nach-rechts-Rechnung und deren Fliesskomma-Variante. Nur dann ist
     * die Kombination diagnostisch brauchbar.
     */
    private Aufgabe b01MitDivision() {
        for (int versuch = 0; versuch < 50; versuch++) {
            int z = rnd(2, 6);
            int y = rnd(4, 30);
            int x = rnd(2, 12);
            int korrekt = x + y / z;
            int linksNachRechts = (x + y) / z;
            boolean restBleibt = (x + y) % z != 0;
            if (korrekt != linksNachRechts && restBleibt) {
                return ohneVars("B01", bin("+", intLit(x), bin("/", intLit(y), intLit(z))));
            }
        }
        return ohneVars("B01", bin("+", intLit(5), bin("/", intLit(9), intLit(2))));
    }

    /**
     * {@code x + y % z}, so gewaehlt, dass korrekte Loesung, Links-nach-rechts-Rechnung und
     * die Variante mit Modulo als Division jeweils verschieden ausfallen.
     */
    private Aufgabe b01MitModulo() {
        for (int versuch = 0; versuch < 50; versuch++) {
            int z = rnd(3, 7);
            int y = rnd(10, 30);
            int x = rnd(2, 12);
            int korrekt = x + y % z;
            int linksNachRechts = (x + y) % z;
            int moduloAlsDivision = (x + y) / z;
            if (korrekt != linksNachRechts && linksNachRechts != moduloAlsDivision) {
                return ohneVars("B01", bin("+", intLit(x), bin("%", intLit(y), intLit(z))));
            }
        }
        return ohneVars("B01", bin("+", intLit(7), bin("%", intLit(20), intLit(6))));
    }

    private Aufgabe b02() { // Ganzzahldivision: x / y (nicht glatt teilbar)
        int y = rnd(2, 9), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B02", bin("/", intLit(x), intLit(y)));
    }

    private Aufgabe b03() { // String-Konkatenation: "NN" + n
        return ohneVars("B03", bin("+", stringLit(String.valueOf(rnd(10, 99))), intLit(rnd(1, 9))));
    }

    private Aufgabe b04() { // Cast: (int) d  mit Nachkommastellen
        double d = rnd(1, 9) + rnd(1, 9) / 10.0;
        return ohneVars("B04", cast(JType.INT, doubleLit(d)));
    }

    private Aufgabe b05() { // Array-Index: a[k + 1]  (k+1 gueltig)
        String[] arr = zufallsArray(rnd(4, 5));
        int k = rnd(0, arr.length - 2);
        EvaluationContext ctx = new EvaluationContext().setzeStringArray("a", arr).setzeInt("k", k);
        return new Aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx, belegungArray(arr, "k", k));
    }

    private Aufgabe b06() { // Method-Chaining: "WORT".substring(1).toLowerCase()
        String w = wortMitLaenge(3);
        return ohneVars("B06", call(call(stringLit(w), "substring", intLit(1)), "toLowerCase"));
    }

    private Aufgabe b07() { // Datentyp: x / y (int-Division)
        int y = rnd(2, 9), x;
        do { x = rnd(3, 20); } while (x % y == 0);
        return ohneVars("B07", bin("/", intLit(x), intLit(y)));
    }

    private Aufgabe b08() { // nicht auswertbar: n + true  ODER  ("NN" + n) - m
        Expr e = random.nextBoolean()
                ? bin("+", intLit(rnd(1, 9)), boolLit(true))
                : bin("-", bin("+", stringLit(String.valueOf(rnd(10, 99))), intLit(rnd(1, 9))), intLit(rnd(1, 9)));
        return ohneVars("B08", e);
    }

    private Aufgabe b09() { // Kurzschluss: false && (x / 0 == 0) -> false, kein Fehler
        return ohneVars("B09", bin("&&", boolLit(false),
                bin("==", bin("/", intLit(rnd(1, 9)), intLit(0)), intLit(0))));
    }

    private Aufgabe b10() { // Modulo: x % y (mit echtem Rest)
        int y = rnd(2, 9), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B10", bin("%", intLit(x), intLit(y)));
    }

    private Aufgabe b11() { // Ternaer: (x > 0) ? a : b
        int x = rnd(-5, 5);
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", x);
        Expr e = ternary(bin(">", var("x"), intLit(0)), intLit(rnd(1, 9)), intLit(rnd(1, 9)));
        return new Aufgabe("B11", e, ctx, "x = " + x);
    }

    private Aufgabe b12() { // Inkrement: ++x oder x++
        int x = rnd(1, 9);
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", x);
        Expr e = random.nextBoolean() ? preInc(var("x")) : postInc(var("x"));
        return new Aufgabe("B12", e, ctx, "x = " + x);
    }

    private Aufgabe b13() { // charAt: "WORT".charAt(i)
        String w = wortMitLaenge(2);
        return ohneVars("B13", call(stringLit(w), "charAt", intLit(rnd(0, w.length() - 1))));
    }

    private Aufgabe b14() { // String-Vergleich: s.equals("wort")
        String w = wort();
        String vergleich = random.nextBoolean() ? w : wort();
        EvaluationContext ctx = new EvaluationContext().setzeString("s", w);
        return new Aufgabe("B14", call(var("s"), "equals", stringLit(vergleich)), ctx, "s = \"" + w + "\"");
    }

    // ---- Hilfsmittel ----

    private int rnd(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private String wort() {
        return WOERTER[random.nextInt(WOERTER.length)];
    }

    private String wortMitLaenge(int minLaenge) {
        String w;
        do { w = wort(); } while (w.length() < minLaenge);
        return w;
    }

    private String[] zufallsArray(int n) {
        String[] a = new String[n];
        for (int i = 0; i < n; i++) a[i] = wort();
        return a;
    }

    private Aufgabe ohneVars(String id, Expr e) {
        return new Aufgabe(id, e, new EvaluationContext(), "");
    }

    private String belegungArray(String[] arr, String name, int wert) {
        StringBuilder sb = new StringBuilder("a = {");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(arr[i]).append("\"");
        }
        return sb.append("}, ").append(name).append(" = ").append(wert).toString();
    }
}