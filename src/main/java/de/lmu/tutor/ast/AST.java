package de.lmu.tutor.ast;

import java.util.List;

/**
 * Kleine Fabrik mit kurzen Methoden, um Ausdrucksbaeume lesbar zu bauen.
 *
 * <p>Statt {@code new Expr.Bin("+", new Expr.Var("k"), new Expr.Lit(1, JType.INT))}
 * schreibt man {@code bin("+", var("k"), intLit(1))}. Am besten per
 * {@code import static de.lmu.tutor.ast.AST.*;} einbinden.</p>
 */
public final class AST {

    private AST() {
    }

    // ---- Literale ----
    public static Expr intLit(int wert)        { return new Expr.Lit(wert, JType.INT); }
    public static Expr doubleLit(double wert)  { return new Expr.Lit(wert, JType.DOUBLE); }
    public static Expr charLit(char wert)      { return new Expr.Lit(wert, JType.CHAR); }
    public static Expr boolLit(boolean wert)   { return new Expr.Lit(wert, JType.BOOL); }
    public static Expr stringLit(String wert)  { return new Expr.Lit(wert, JType.STRING); }

    // ---- Variablen ----
    public static Expr var(String name)        { return new Expr.Var(name); }

    // ---- Operatoren ----
    public static Expr bin(String op, Expr links, Expr rechts) { return new Expr.Bin(op, links, rechts); }
    public static Expr not(Expr e)             { return new Expr.Unary("!", e); }
    public static Expr neg(Expr e)             { return new Expr.Unary("-", e); }
    public static Expr cast(JType zielTyp, Expr e) { return new Expr.Cast(zielTyp, e); }

    // ---- Zugriffe und Aufrufe ----
    public static Expr index(Expr array, Expr indexAusdruck) { return new Expr.Index(array, indexAusdruck); }
    public static Expr call(Expr empfaenger, String methode, Expr... argumente) {
        return new Expr.Call(empfaenger, methode, List.of(argumente));
    }
    /** Statischer Methodenaufruf, z. B. staticCall("Double", "parseDouble", index(...)). */
    public static Expr staticCall(String klasse, String methode, Expr... argumente) {
        return new Expr.StaticCall(klasse, methode, List.of(argumente));
    }

    // ---- Ternaer und Inkrement ----
    public static Expr ternary(Expr bedingung, Expr dann, Expr sonst) { return new Expr.Ternary(bedingung, dann, sonst); }
    public static Expr preInc(Expr ziel)       { return new Expr.IncDec("++", true, ziel); }
    public static Expr postInc(Expr ziel)      { return new Expr.IncDec("++", false, ziel); }
    public static Expr preDec(Expr ziel)       { return new Expr.IncDec("--", true, ziel); }
    public static Expr postDec(Expr ziel)      { return new Expr.IncDec("--", false, ziel); }
}