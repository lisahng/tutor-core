package de.lmu.tutor.eval;

import de.lmu.tutor.ast.JType;

/**
 * Ein ausgewerteter Wert samt Datentyp, z. B. 10 vom Typ int oder "gruen" vom Typ String.
 *
 * <p>Das ist das Ergebnis, das der Evaluator fuer jeden Teilausdruck liefert -- immer
 * ein Wert UND sein Typ, genau wie es die Aufgaben verlangen.</p>
 */
public record Value(JType typ, Object wert) {

    // ---- Fabrikmethoden ----
    public static Value ofInt(int w)        { return new Value(JType.INT, w); }
    public static Value ofDouble(double w)  { return new Value(JType.DOUBLE, w); }
    public static Value ofChar(char w)      { return new Value(JType.CHAR, w); }
    public static Value ofBool(boolean w)   { return new Value(JType.BOOL, w); }
    public static Value ofString(String w)  { return new Value(JType.STRING, w); }
    public static Value ofIntArray(int[] w)      { return new Value(JType.INT_ARRAY, w); }
    public static Value ofStringArray(String[] w){ return new Value(JType.STRING_ARRAY, w); }

    // ---- Zugriffshelfer ----
    public int asInt()        { return (int) wert; }
    public double asDouble()  { return ((Number) wert).doubleValue(); }
    public char asChar()      { return (char) wert; }
    public boolean asBool()   { return (boolean) wert; }
    public String asString()  { return (String) wert; }
    public int[] asIntArray()       { return (int[]) wert; }
    public String[] asStringArray() { return (String[]) wert; }

    /** Numerischer Wert als double -- fuer int, double und char (char -> Zeichencode). */
    public double alsZahl() {
        return switch (typ) {
            case INT -> (int) wert;
            case DOUBLE -> (double) wert;
            case CHAR -> (char) wert;
            default -> throw new IllegalStateException(typ + " ist nicht numerisch");
        };
    }

    /** Ganzzahliger Wert -- fuer int und char. */
    public int alsGanzzahl() {
        return switch (typ) {
            case INT -> (int) wert;
            case CHAR -> (char) wert;
            default -> throw new IllegalStateException(typ + " ist nicht ganzzahlig");
        };
    }

    /** Java-nahe Darstellung: Strings in "", char in '', Arrays in {}. */
    public String render() {
        return switch (typ) {
            case STRING -> "\"" + wert + "\"";
            case CHAR -> "'" + wert + "'";
            case INT_ARRAY -> arrayText((int[]) wert);
            case STRING_ARRAY -> arrayText((String[]) wert);
            default -> String.valueOf(wert);
        };
    }

    /** Wert mit Datentyp, z. B. "10 : int". */
    public String mitTyp() {
        return render() + " : " + typ.javaName();
    }

    private static String arrayText(int[] a) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(", "); sb.append(a[i]); }
        return sb.append("}").toString();
    }

    private static String arrayText(String[] a) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(", "); sb.append("\"").append(a[i]).append("\""); }
        return sb.append("}").toString();
    }
}