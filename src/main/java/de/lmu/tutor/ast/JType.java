package de.lmu.tutor.ast;

/**
 * Die Datentypen, die im Sprachumfang des Tutorsystems vorkommen.
 *
 * <p>JType ist das gemeinsame "Vokabular" fuer Typen. In Schritt 2 (Ausdrucksbaum)
 * nutzen wir es nur, um Literale (z. B. 5, 2.1, "HEY") und Cast-Ziele (z. B. (int))
 * zu kennzeichnen. In Schritt 3 (Evaluator) berechnet damit der Auswerter den
 * Datentyp des Gesamtausdrucks.</p>
 */
public enum JType {
    INT("int"),
    DOUBLE("double"),
    CHAR("char"),
    BOOL("boolean"),
    STRING("String"),
    INT_ARRAY("int[]"),
    STRING_ARRAY("String[]");

    private final String javaName;

    JType(String javaName) {
        this.javaName = javaName;
    }

    /** Der Name, wie er in Java-Quelltext geschrieben wird (z. B. "int"). */
    public String javaName() {
        return javaName;
    }

    /** Zahltyp? (int oder double) */
    public boolean isNumeric() {
        return this == INT || this == DOUBLE;
    }

    /** Array-Typ? */
    public boolean isArray() {
        return this == INT_ARRAY || this == STRING_ARRAY;
    }

    /** Elementtyp eines Arrays, z. B. STRING fuer STRING_ARRAY. */
    public JType elementType() {
        return switch (this) {
            case INT_ARRAY -> INT;
            case STRING_ARRAY -> STRING;
            default -> throw new IllegalStateException(javaName + " ist kein Array-Typ");
        };
    }
}
