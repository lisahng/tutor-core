package de.lmu.tutor.feedback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Loest Platzhalter in Feedback-Texten gegen die tatsaechlich gestellte Aufgabe auf.
 *
 * <p>Hintergrund: Die dritte Scaffolding-Stufe soll den Auswertungsschritt konkret
 * vorrechnen. Steht sie als fester Text in der Bug Library, nennt sie immer dasselbe
 * Beispiel. Bei einer generierten Aufgabe passt das nie, und die Lernende sieht bei
 * {@code 25 / 6} eine Erklaerung ueber {@code 20 / 3}. Die Stufe verfehlt damit genau
 * das, wofuer sie gedacht ist.</p>
 *
 * <p>Deshalb enthalten die Texte Platzhalter in geschweiften Klammern, die hier gefuellt
 * werden. Immer verfuegbar sind:</p>
 *
 * <ul>
 *   <li>{@code {ausdruck}} - der Ausdruck als Java-Quelltext</li>
 *   <li>{@code {wert}} - der korrekte Wert</li>
 *   <li>{@code {typ}} - dessen Datentyp</li>
 *   <li>{@code {ergebnis}} - Wert und Typ zusammen, etwa {@code 4 : int}</li>
 *   <li>{@code {belegung}} - die Variablenbelegung, falls vorhanden</li>
 *   <li>{@code {grund}} - warum der Ausdruck nicht auswertbar ist</li>
 * </ul>
 *
 * <p>Je nach Form des Ausdrucks kommen weitere hinzu, etwa {@code {links}} und
 * {@code {rechts}} bei einem binaeren Operator oder {@code {index}} bei einem
 * Array-Zugriff. Welche das sind, steht bei den jeweiligen Methoden weiter unten.</p>
 *
 * <p>Laesst sich ein Platzhalter nicht aufloesen, liefert {@link #fuelle(String)} ein
 * leeres Optional. Der {@link Feedbackgenerator} weicht dann auf einen allgemeinen Text
 * aus, statt eine Rueckmeldung mit sichtbaren Klammern auszugeben. Das kann vorkommen,
 * wenn die diagnostizierte Kategorie nicht zur Form der Aufgabe passt, etwa wenn bei
 * einer Array-Aufgabe ein Praezedenzfehler erkannt wird.</p>
 */
public final class Platzhalter {

    private final Map<String, String> werte = new HashMap<>();

    public Platzhalter(Aufgabe aufgabe, EvaluationResult referenz) {
        allgemeines(aufgabe, referenz);
        nachAusdrucksform(aufgabe.ausdruck(), aufgabe.kontext());
    }

    // ---------------------------------------------------------------
    // Fuellen
    // ---------------------------------------------------------------

    /**
     * Ersetzt alle Platzhalter im Text.
     *
     * @return der gefuellte Text, oder ein leeres Optional, sobald ein Platzhalter
     *         nicht aufloesbar ist
     */
    public Optional<String> fuelle(String vorlage) {
        StringBuilder ergebnis = new StringBuilder();
        int position = 0;
        while (position < vorlage.length()) {
            int auf = vorlage.indexOf('{', position);
            if (auf < 0) {
                ergebnis.append(vorlage, position, vorlage.length());
                break;
            }
            int zu = vorlage.indexOf('}', auf);
            if (zu < 0) {
                ergebnis.append(vorlage, position, vorlage.length());
                break;
            }
            ergebnis.append(vorlage, position, auf);
            String name = vorlage.substring(auf + 1, zu);
            String wert = werte.get(name);
            if (wert == null || wert.isBlank()) {
                return Optional.empty();
            }
            ergebnis.append(wert);
            position = zu + 1;
        }
        return Optional.of(ergebnis.toString());
    }

    /** Ob dieser Platzhalter aufgeloest werden kann. Vor allem fuer Tests gedacht. */
    public boolean kennt(String name) {
        String wert = werte.get(name);
        return wert != null && !wert.isBlank();
    }

    // ---------------------------------------------------------------
    // Befuellen
    // ---------------------------------------------------------------

    private void allgemeines(Aufgabe aufgabe, EvaluationResult referenz) {
        setze("ausdruck", aufgabe.render());
        setze("belegung", aufgabe.belegung());
        if (referenz.auswertbar()) {
            setze("wert", referenz.wert().render());
            setze("typ", referenz.wert().typ().javaName());
            setze("ergebnis", referenz.wert().mitTyp());
        } else {
            setze("grund", referenz.nichtAuswertbarGrund());
            setze("ergebnis", "nicht auswertbar");
        }
    }

    /**
     * Ergaenzt die Platzhalter, die von der Form des Ausdrucks abhaengen.
     *
     * <p>Binaerer Operator: {@code {links}}, {@code {rechts}} als Quelltext sowie
     * {@code {linksWert}}, {@code {rechtsWert}} als berechnete Werte.<br>
     * Cast: {@code {operand}}, {@code {operandWert}}.<br>
     * Array-Zugriff: {@code {array}}, {@code {index}}, {@code {indexWert}}.<br>
     * Methodenaufruf: {@code {empfaenger}}, {@code {methode}}; bei einer Kette
     * zusaetzlich {@code {innereMethode}}, {@code {aeussereMethode}} und
     * {@code {zwischenwert}}.<br>
     * Ternaerer Operator: {@code {bedingung}}, {@code {bedingungWert}}, {@code {dann}},
     * {@code {sonst}}.<br>
     * Inkrement: {@code {variable}}, {@code {neuerWert}}.</p>
     */
    private void nachAusdrucksform(Expr wurzel, EvaluationContext ctx) {
        switch (wurzel) {
            case Expr.Bin b -> {
                setze("links", b.links().render());
                setze("rechts", b.rechts().render());
                werte(b.links(), ctx).ifPresent(v -> setze("linksWert", v.render()));
                werte(b.rechts(), ctx).ifPresent(v -> setze("rechtsWert", v.render()));
            }
            case Expr.Cast c -> {
                setze("operand", c.operand().render());
                werte(c.operand(), ctx).ifPresent(v -> setze("operandWert", v.render()));
            }
            case Expr.Index ix -> {
                setze("array", ix.array().render());
                setze("index", ix.indexAusdruck().render());
                werte(ix.indexAusdruck(), ctx).ifPresent(v -> setze("indexWert", v.render()));
            }
            case Expr.Call aussen -> {
                setze("methode", aussen.methode());
                setze("empfaenger", aussen.empfaenger().render());
                setze("aeussereMethode", aussen.methode() + "(...)");
                if (aussen.empfaenger() instanceof Expr.Call innen) {
                    setze("innereMethode", innen.methode() + "(...)");
                    werte(innen, ctx).ifPresent(v -> setze("zwischenwert", v.render()));
                }
            }
            case Expr.Ternary t -> {
                setze("bedingung", t.bedingung().render());
                setze("dann", t.dann().render());
                setze("sonst", t.sonst().render());
                werte(t.bedingung(), ctx).ifPresent(v -> setze("bedingungWert", v.render()));
            }
            case Expr.IncDec d -> {
                setze("variable", d.ziel().render());
                // Nach der Auswertung steht in der Variablen bereits der neue Wert.
                werte(d.ziel(), ctx).ifPresent(v -> setze("neuerWert", v.render()));
            }
            default -> {
                // Literale und Variablen brauchen keine zusaetzlichen Platzhalter.
            }
        }
    }

    /** Wertet einen Teilausdruck aus; leer, wenn er nicht auswertbar ist. */
    private Optional<Value> werte(Expr e, EvaluationContext ctx) {
        EvaluationResult r = new StepEvaluator().evaluate(e, ctx);
        return r.auswertbar() ? Optional.of(r.wert()) : Optional.empty();
    }

    private void setze(String name, String wert) {
        if (wert != null && !wert.isBlank()) {
            werte.put(name, wert);
        }
    }
}