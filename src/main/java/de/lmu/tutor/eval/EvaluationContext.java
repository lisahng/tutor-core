package de.lmu.tutor.eval;

import java.util.HashMap;
import java.util.Map;

/**
 * Die Variablenbelegung fuer eine Auswertung, z. B. k = 2 und a = {"rot","gelb","blau","gruen"}.
 *
 * <p>Der Evaluator schlaegt hier Variablen nach. Inkrement/Dekrement (++/--) veraendern
 * den Wert einer Variable und schreiben ihn ueber {@link #setze} zurueck.</p>
 */
public final class EvaluationContext {

    private final Map<String, Value> variablen = new HashMap<>();

    /** Setzt eine Variable auf einen fertigen Wert. */
    public EvaluationContext setze(String name, Value wert) {
        variablen.put(name, wert);
        return this;
    }

    // ---- bequeme Kurzformen ----
    public EvaluationContext setzeInt(String name, int w)          { return setze(name, Value.ofInt(w)); }
    public EvaluationContext setzeDouble(String name, double w)    { return setze(name, Value.ofDouble(w)); }
    public EvaluationContext setzeChar(String name, char w)        { return setze(name, Value.ofChar(w)); }
    public EvaluationContext setzeBool(String name, boolean w)     { return setze(name, Value.ofBool(w)); }
    public EvaluationContext setzeString(String name, String w)    { return setze(name, Value.ofString(w)); }
    public EvaluationContext setzeIntArray(String name, int[] w)       { return setze(name, Value.ofIntArray(w)); }
    public EvaluationContext setzeStringArray(String name, String[] w) { return setze(name, Value.ofStringArray(w)); }

    /** Ob die Variable bekannt ist. */
    public boolean kennt(String name) {
        return variablen.containsKey(name);
    }

    /** Liefert den Wert der Variable oder null, wenn sie unbekannt ist. */
    public Value hole(String name) {
        return variablen.get(name);
    }
}