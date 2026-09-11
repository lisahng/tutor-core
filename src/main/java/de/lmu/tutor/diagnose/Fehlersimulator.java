package de.lmu.tutor.diagnose;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;

/**
 * Simuliert einen Teil der Bug-Library-Kategorien als "gestoerte" Auswertungsregeln
 * und prueft, ob eine davon genau den von der Nutzerin abgegebenen Fehlwert erklaeren
 * kann (Perturbationsmodell)
 *
 * <p>Nicht jede Kategorie laesst sich generisch simulieren (z. B. B06 Method-Chaining
 * oder B11 logisch/bitweise haengen zu stark vom Einzelfall ab); fuer diese greift im
 * {@link Fehlerklassifikator} die Zielkategorie- bzw. Struktur-Heuristik.</p>
 *
 * <p>Hinweis: Die hier verwendeten Bug-IDs (B01-B14) folgen der aktuell implementierten
 * {@code bug-library.json} dieses Projekts.</p>
 */
public final class Fehlersimulator {

    private final StepEvaluator evaluator = new StepEvaluator();

    /** Ein simulierter Fehlwert samt der Bug-ID, deren Regel ihn erzeugt hat. */
    public record Treffer(String bugId, Value wert, String erklaerung) {
    }

    /** Fuehrt alle anwendbaren Simulationen fuer {@code wurzel} aus und liefert die erfolgreichen. */
    public List<Treffer> simuliereAlle(Expr wurzel, EvaluationContext ctx) {
        List<Treffer> treffer = new ArrayList<>();
        b01LinksNachRechts(wurzel, ctx).ifPresent(treffer::add);
        b02GanzzahldivisionAlsFliesskomma(wurzel, ctx).ifPresent(treffer::add);
        b03FehlendeDoublePromotion(wurzel, ctx).ifPresent(treffer::add);
        b04ModuloAlsQuotient(wurzel, ctx).ifPresent(treffer::add);
        b05IndexOhneVersatz(wurzel, ctx).ifPresent(treffer::add);
        b10StringAlsArithmetik(wurzel, ctx).ifPresent(treffer::add);
        b12OperatorUebersehen(wurzel, ctx).ifPresent(treffer::add);
        return treffer;
    }

    // ---- B01: Operatorpraezedenz missachtet -> strikt links-nach-rechts ----
    private Optional<Treffer> b01LinksNachRechts(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin) || !istArithmetischeKette(wurzel)) {
            return Optional.empty();
        }
        List<Value> operanden = new ArrayList<>();
        List<String> operatoren = new ArrayList<>();
        if (!sammleKette(wurzel, ctx, operanden, operatoren) || operanden.size() < 2) {
            return Optional.empty();
        }
        for (Value v : operanden) {
            if (!v.typ().isNumeric()) {
                return Optional.empty(); // z. B. String-Konkatenation -- hier nicht simulierbar, siehe B10
            }
        }
        Value ergebnis = operanden.get(0);
        for (int i = 0; i < operatoren.size(); i++) {
            ergebnis = kombiniere(operatoren.get(i), ergebnis, operanden.get(i + 1));
        }
        return Optional.of(new Treffer("B01", ergebnis,
                "Links-nach-rechts ausgewertet statt Punkt- vor Strichrechnung zu beachten."));
    }

    /** Nur +,-,*,/,% -- diese Ketten hat auch der AufgabenGenerator (Vorlage x + y * z). */
    private boolean istArithmetischeKette(Expr e) {
        if (e instanceof Expr.Bin b) {
            return istArithmetikOp(b.op()) && istArithmetischeKetteOderBlatt(b.links()) && istArithmetischeKetteOderBlatt(b.rechts());
        }
        return false;
    }

    private boolean istArithmetischeKetteOderBlatt(Expr e) {
        if (e instanceof Expr.Bin b) {
            return istArithmetikOp(b.op()) && istArithmetischeKetteOderBlatt(b.links()) && istArithmetischeKetteOderBlatt(b.rechts());
        }
        return true; // Blatt (Lit/Var/...) -- wird ganz normal ausgewertet
    }

    private boolean istArithmetikOp(String op) {
        return op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%");
    }

    /** Sammelt Operanden und Operatoren einer arithmetischen Kette in Schreibreihenfolge (Inorder). */
    private boolean sammleKette(Expr e, EvaluationContext ctx, List<Value> operanden, List<String> operatoren) {
        if (e instanceof Expr.Bin b && istArithmetikOp(b.op())
                && istArithmetischeKetteOderBlatt(b.links()) && istArithmetischeKetteOderBlatt(b.rechts())) {
            if (b.links() instanceof Expr.Bin) {
                if (!sammleKette(b.links(), ctx, operanden, operatoren)) return false;
            } else {
                Optional<Value> v = werteBlatt(b.links(), ctx);
                if (v.isEmpty()) return false;
                operanden.add(v.get());
            }
            operatoren.add(b.op());
            if (b.rechts() instanceof Expr.Bin) {
                if (!sammleKette(b.rechts(), ctx, operanden, operatoren)) return false;
            } else {
                Optional<Value> v = werteBlatt(b.rechts(), ctx);
                if (v.isEmpty()) return false;
                operanden.add(v.get());
            }
            return true;
        }
        return false;
    }

    private Optional<Value> werteBlatt(Expr e, EvaluationContext ctx) {
        EvaluationResult r = evaluator.evaluate(e, ctx);
        return r.auswertbar() ? Optional.of(r.wert()) : Optional.empty();
    }

    /** Vereinfachte Arithmetik ohne Praezedenz -- bewusst separat von StepEvaluator.arithmetik(), das intern bleibt. */
    private Value kombiniere(String op, Value l, Value r) {
        boolean doppelt = l.typ() == JType.DOUBLE || r.typ() == JType.DOUBLE;
        double a = l.alsZahl(), c = r.alsZahl();
        if (doppelt) {
            return Value.ofDouble(rechneDouble(op, a, c));
        }
        int ai = l.alsGanzzahl(), ci = r.alsGanzzahl();
        if ((op.equals("/") || op.equals("%")) && ci == 0) {
            return Value.ofDouble(rechneDouble(op, a, c)); // Sonderfall: nicht simulierbar ohne Division durch Null -> als double melden
        }
        return Value.ofInt((int) rechneDouble(op, ai, ci));
    }

    private double rechneDouble(String op, double a, double c) {
        return switch (op) {
            case "+" -> a + c;
            case "-" -> a - c;
            case "*" -> a * c;
            case "/" -> a / c;
            case "%" -> a % c;
            default -> 0.0;
        };
    }

    // ---- B02: Ganzzahldivision uebersehen -> echte (Fliesskomma-)Division statt Truncation ----
    private Optional<Treffer> b02GanzzahldivisionAlsFliesskomma(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("/")) {
            return Optional.empty();
        }
        Optional<Value> l = werteBlatt(b.links(), ctx);
        Optional<Value> r = werteBlatt(b.rechts(), ctx);
        if (l.isEmpty() || r.isEmpty()) return Optional.empty();
        if (l.get().typ() != JType.INT || r.get().typ() != JType.INT) return Optional.empty(); // nur relevant, wenn Referenz int/int ist
        if (r.get().asInt() == 0) return Optional.empty();
        double buggy = l.get().asInt() / (double) r.get().asInt();
        return Optional.of(new Treffer("B02", Value.ofDouble(buggy),
                "Fliesskommadivision angenommen statt Ganzzahldivision (Nachkommastellen nicht abgeschnitten)."));
    }

    // ---- B03: Implizite Typkonversion uebersehen -> double-Promotion ignoriert, int-Division angenommen ----
    private Optional<Treffer> b03FehlendeDoublePromotion(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !(b.op().equals("/") || b.op().equals("*") || b.op().equals("+") || b.op().equals("-"))) {
            return Optional.empty();
        }
        Optional<Value> l = werteBlatt(b.links(), ctx);
        Optional<Value> r = werteBlatt(b.rechts(), ctx);
        if (l.isEmpty() || r.isEmpty()) return Optional.empty();
        boolean genauEinDouble = (l.get().typ() == JType.DOUBLE) ^ (r.get().typ() == JType.DOUBLE);
        boolean beideNumerisch = l.get().typ().isNumeric() && r.get().typ().isNumeric();
        if (!genauEinDouble || !beideNumerisch) return Optional.empty();
        double korrekt = rechneDouble(b.op(), l.get().alsZahl(), r.get().alsZahl());
        return Optional.of(new Treffer("B03", Value.ofInt((int) korrekt),
                "double-Promotion eines Operanden uebersehen, als int-Operation gerechnet."));
    }

    // ---- B04: Modulo als Quotient statt Rest interpretiert ----
    private Optional<Treffer> b04ModuloAlsQuotient(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("%")) {
            return Optional.empty();
        }
        Optional<Value> l = werteBlatt(b.links(), ctx);
        Optional<Value> r = werteBlatt(b.rechts(), ctx);
        if (l.isEmpty() || r.isEmpty() || l.get().typ() != JType.INT || r.get().typ() != JType.INT) return Optional.empty();
        if (r.get().asInt() == 0) return Optional.empty();
        return Optional.of(new Treffer("B04", Value.ofInt(l.get().asInt() / r.get().asInt()),
                "Quotient statt Rest berechnet."));
    }

    // ---- B05: Index-Versatz (z. B. +1) beim Array-Zugriff uebersehen ----
    private Optional<Treffer> b05IndexOhneVersatz(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Index ix) || !(ix.indexAusdruck() instanceof Expr.Bin b)) {
            return Optional.empty();
        }
        if (!(b.op().equals("+") || b.op().equals("-"))) return Optional.empty();
        Expr basis;
        if (b.rechts() instanceof Expr.Lit) {
            basis = b.links();
        } else if (b.links() instanceof Expr.Lit) {
            basis = b.rechts();
        } else {
            return Optional.empty();
        }
        Optional<Value> arr = werteBlatt(ix.array(), ctx);
        Optional<Value> idx = werteBlatt(basis, ctx);
        if (arr.isEmpty() || idx.isEmpty() || idx.get().typ() != JType.INT) return Optional.empty();
        int i = idx.get().asInt();
        if (arr.get().typ() == JType.STRING_ARRAY) {
            String[] a = arr.get().asStringArray();
            if (i < 0 || i >= a.length) return Optional.empty();
            return Optional.of(new Treffer("B05", Value.ofString(a[i]),
                    "Versatz im Indexausdruck (z. B. + 1) beim Array-Zugriff ignoriert."));
        }
        if (arr.get().typ() == JType.INT_ARRAY) {
            int[] a = arr.get().asIntArray();
            if (i < 0 || i >= a.length) return Optional.empty();
            return Optional.of(new Treffer("B05", Value.ofInt(a[i]),
                    "Versatz im Indexausdruck (z. B. + 1) beim Array-Zugriff ignoriert."));
        }
        return Optional.empty();
    }

    // ---- B10: String-Konkatenation als Arithmetik missverstanden ----
    private Optional<Treffer> b10StringAlsArithmetik(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("+")) {
            return Optional.empty();
        }
        Optional<Value> l = werteBlatt(b.links(), ctx);
        Optional<Value> r = werteBlatt(b.rechts(), ctx);
        if (l.isEmpty() || r.isEmpty()) return Optional.empty();
        boolean genauEinString = (l.get().typ() == JType.STRING) ^ (r.get().typ() == JType.STRING);
        if (!genauEinString) return Optional.empty();
        Value stringSeite = l.get().typ() == JType.STRING ? l.get() : r.get();
        Value zahlSeite = l.get().typ() == JType.STRING ? r.get() : l.get();
        if (!zahlSeite.typ().isNumeric()) return Optional.empty();
        Double alsZahl = parseAlsZahl(stringSeite.asString());
        if (alsZahl == null) return Optional.empty();
        double summe = alsZahl + zahlSeite.alsZahl();
        return Optional.of(new Treffer("B10",
                (summe == Math.rint(summe)) ? Value.ofInt((int) summe) : Value.ofDouble(summe),
                "Zahlenaehnlichen String arithmetisch addiert statt zu verketten."));
    }

    private Double parseAlsZahl(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- B12: Unaerer Operator (!, -) beim Auswerten schlicht uebersehen ----
    private Optional<Treffer> b12OperatorUebersehen(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Unary u)) {
            return Optional.empty();
        }
        Optional<Value> operand = werteBlatt(u.operand(), ctx);
        if (operand.isEmpty()) return Optional.empty();
        return Optional.of(new Treffer("B12", operand.get(),
                "Der unaere Operator (" + u.op() + ") wurde bei der Auswertung nicht angewendet."));
    }
}