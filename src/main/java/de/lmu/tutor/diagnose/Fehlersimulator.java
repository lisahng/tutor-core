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
 * Simuliert typische Fehlvorstellungen als "gestoerte" Auswertungsregeln und prueft, ob
 * eine davon genau den Wert erzeugt, den die Lernende abgegeben hat.
 *
 * <p>Der Ansatz folgt dem Perturbationsmodell von Brown und VanLehn (1980): Wer einen
 * Fehler macht, hat meist keine Wissensluecke, sondern eine systematisch abgewandelte
 * Regel im Kopf. Laesst sich der falsche Wert mit einer solchen Regel nachrechnen, ist
 * das die plausibelste Erklaerung fuer den Fehler.</p>
 *
 * <p>Nicht jede Kategorie der Bug Library braucht eine Simulation. B07, B08, B09 und B13
 * erkennt der {@link Fehlerklassifikator} bereits an der Signatur der Antwort, etwa
 * daran, dass der Wert stimmt und nur der Datentyp abweicht.</p>
 *
 * <p>Die IDs folgen der Nummerierung aus Anhang A.1 der Zulassungsarbeit, ebenso wie
 * {@code bug-library.json} und der {@code AufgabenGenerator}.</p>
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
        b03StringAlsArithmetik(wurzel, ctx).ifPresent(treffer::add);
        b04CastRundetStattAbzuschneiden(wurzel, ctx).ifPresent(treffer::add);
        b04FehlendeDoublePromotion(wurzel, ctx).ifPresent(treffer::add);
        b05IndexOhneVersatz(wurzel, ctx).ifPresent(treffer::add);
        treffer.addAll(b06KetteUnvollstaendig(wurzel, ctx));
        b10ModuloAlsQuotient(wurzel, ctx).ifPresent(treffer::add);
        b11FalscherZweig(wurzel, ctx).ifPresent(treffer::add);
        b12PraefixPostfixVerwechselt(wurzel, ctx).ifPresent(treffer::add);
        treffer.addAll(b14ReferenzStattInhalt(wurzel, ctx));
        return treffer;
    }

    // ================================================================
    // B01: Operatorpraezedenz missachtet, strikt links nach rechts
    // ================================================================

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
                return Optional.empty(); // String-Konkatenation, siehe B03
            }
        }
        Value ergebnis = operanden.get(0);
        for (int i = 0; i < operatoren.size(); i++) {
            ergebnis = kombiniere(operatoren.get(i), ergebnis, operanden.get(i + 1));
        }
        return Optional.of(new Treffer("B01", ergebnis,
                "Links nach rechts ausgewertet, statt Punkt- vor Strichrechnung zu beachten."));
    }

    /** Nur +,-,*,/,% bilden eine Kette, in der die Praezedenz ueberhaupt eine Rolle spielt. */
    private boolean istArithmetischeKette(Expr e) {
        return e instanceof Expr.Bin b
                && istArithmetikOp(b.op())
                && istKetteOderBlatt(b.links())
                && istKetteOderBlatt(b.rechts());
    }

    private boolean istKetteOderBlatt(Expr e) {
        if (e instanceof Expr.Bin b) {
            return istArithmetikOp(b.op()) && istKetteOderBlatt(b.links()) && istKetteOderBlatt(b.rechts());
        }
        return true;
    }

    private boolean istArithmetikOp(String op) {
        return op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%");
    }

    /** Sammelt Operanden und Operatoren der Kette in Schreibreihenfolge. */
    private boolean sammleKette(Expr e, EvaluationContext ctx, List<Value> operanden, List<String> operatoren) {
        if (!(e instanceof Expr.Bin b) || !istArithmetikOp(b.op())
                || !istKetteOderBlatt(b.links()) || !istKetteOderBlatt(b.rechts())) {
            return false;
        }
        if (!sammleSeite(b.links(), ctx, operanden, operatoren)) {
            return false;
        }
        operatoren.add(b.op());
        return sammleSeite(b.rechts(), ctx, operanden, operatoren);
    }

    private boolean sammleSeite(Expr seite, EvaluationContext ctx, List<Value> operanden, List<String> operatoren) {
        if (seite instanceof Expr.Bin) {
            return sammleKette(seite, ctx, operanden, operatoren);
        }
        Optional<Value> wert = werteBlatt(seite, ctx);
        if (wert.isEmpty()) {
            return false;
        }
        operanden.add(wert.get());
        return true;
    }

    /**
     * Vereinfachte Arithmetik ohne Praezedenz. Bewusst eine eigene Umsetzung und kein
     * Zugriff auf den StepEvaluator, denn dieser wertet ja gerade korrekt aus.
     */
    private Value kombiniere(String op, Value links, Value rechts) {
        boolean alsDouble = links.typ() == JType.DOUBLE || rechts.typ() == JType.DOUBLE;
        double a = links.alsZahl();
        double b = rechts.alsZahl();
        if (alsDouble) {
            return Value.ofDouble(rechne(op, a, b));
        }
        if ((op.equals("/") || op.equals("%")) && rechts.alsGanzzahl() == 0) {
            return Value.ofDouble(rechne(op, a, b)); // Division durch Null nicht simulierbar
        }
        return Value.ofInt((int) rechne(op, links.alsGanzzahl(), rechts.alsGanzzahl()));
    }

    private double rechne(String op, double a, double b) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            case "%" -> a % b;
            default -> 0.0;
        };
    }

    // ================================================================
    // B02: Ganzzahldivision als Fliesskommadivision gelesen
    // ================================================================

    private Optional<Treffer> b02GanzzahldivisionAlsFliesskomma(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("/")) {
            return Optional.empty();
        }
        Optional<Value> links = werteBlatt(b.links(), ctx);
        Optional<Value> rechts = werteBlatt(b.rechts(), ctx);
        if (links.isEmpty() || rechts.isEmpty()
                || links.get().typ() != JType.INT || rechts.get().typ() != JType.INT
                || rechts.get().asInt() == 0) {
            return Optional.empty();
        }
        double buggy = links.get().asInt() / (double) rechts.get().asInt();
        return Optional.of(new Treffer("B02", Value.ofDouble(buggy),
                "Fliesskommadivision angenommen, die Nachkommastellen wurden nicht abgeschnitten."));
    }

    // ================================================================
    // B03: String-Konkatenation als Addition gelesen
    // ================================================================

    private Optional<Treffer> b03StringAlsArithmetik(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("+")) {
            return Optional.empty();
        }
        Optional<Value> links = werteBlatt(b.links(), ctx);
        Optional<Value> rechts = werteBlatt(b.rechts(), ctx);
        if (links.isEmpty() || rechts.isEmpty()) {
            return Optional.empty();
        }
        boolean genauEinString = (links.get().typ() == JType.STRING) ^ (rechts.get().typ() == JType.STRING);
        if (!genauEinString) {
            return Optional.empty();
        }
        Value stringSeite = links.get().typ() == JType.STRING ? links.get() : rechts.get();
        Value zahlSeite = links.get().typ() == JType.STRING ? rechts.get() : links.get();
        if (!zahlSeite.typ().isNumeric()) {
            return Optional.empty();
        }
        Double alsZahl = parseZahl(stringSeite.asString());
        if (alsZahl == null) {
            return Optional.empty();
        }
        double summe = alsZahl + zahlSeite.alsZahl();
        Value buggy = (summe == Math.rint(summe)) ? Value.ofInt((int) summe) : Value.ofDouble(summe);
        return Optional.of(new Treffer("B03", buggy,
                "Der zahlenaehnliche String wurde arithmetisch addiert statt verkettet."));
    }

    private Double parseZahl(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException keineZahl) {
            return null;
        }
    }

    // ================================================================
    // B04: Cast und Type-Promotion
    // ================================================================

    /** Der Cast nach int wird als Rundung gelesen statt als Abschneiden. */
    private Optional<Treffer> b04CastRundetStattAbzuschneiden(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Cast c) || c.zielTyp() != JType.INT) {
            return Optional.empty();
        }
        Optional<Value> operand = werteBlatt(c.operand(), ctx);
        if (operand.isEmpty() || operand.get().typ() != JType.DOUBLE) {
            return Optional.empty();
        }
        long gerundet = Math.round(operand.get().alsZahl());
        if (gerundet == (long) operand.get().alsZahl()) {
            return Optional.empty(); // Runden und Abschneiden fallen zusammen, nicht unterscheidbar
        }
        return Optional.of(new Treffer("B04", Value.ofInt((int) gerundet),
                "Der Cast (int) wurde als Rundung gelesen, er schneidet aber ab."));
    }

    /** Die Umwandlung nach double wird uebersehen, es wird ganzzahlig gerechnet. */
    private Optional<Treffer> b04FehlendeDoublePromotion(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b)
                || !(b.op().equals("/") || b.op().equals("*") || b.op().equals("+") || b.op().equals("-"))) {
            return Optional.empty();
        }
        Optional<Value> links = werteBlatt(b.links(), ctx);
        Optional<Value> rechts = werteBlatt(b.rechts(), ctx);
        if (links.isEmpty() || rechts.isEmpty()
                || !links.get().typ().isNumeric() || !rechts.get().typ().isNumeric()) {
            return Optional.empty();
        }
        boolean genauEinDouble = (links.get().typ() == JType.DOUBLE) ^ (rechts.get().typ() == JType.DOUBLE);
        if (!genauEinDouble) {
            return Optional.empty();
        }
        double korrekt = rechne(b.op(), links.get().alsZahl(), rechts.get().alsZahl());
        return Optional.of(new Treffer("B04", Value.ofInt((int) korrekt),
                "Die Umwandlung eines Operanden nach double wurde uebersehen, es wurde ganzzahlig gerechnet."));
    }

    // ================================================================
    // B05: Versatz im Indexausdruck ignoriert
    // ================================================================

    private Optional<Treffer> b05IndexOhneVersatz(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Index ix) || !(ix.indexAusdruck() instanceof Expr.Bin b)) {
            return Optional.empty();
        }
        if (!(b.op().equals("+") || b.op().equals("-"))) {
            return Optional.empty();
        }
        Expr basis;
        if (b.rechts() instanceof Expr.Lit) {
            basis = b.links();
        } else if (b.links() instanceof Expr.Lit) {
            basis = b.rechts();
        } else {
            return Optional.empty();
        }
        Optional<Value> array = werteBlatt(ix.array(), ctx);
        Optional<Value> index = werteBlatt(basis, ctx);
        if (array.isEmpty() || index.isEmpty() || index.get().typ() != JType.INT) {
            return Optional.empty();
        }
        int i = index.get().asInt();
        String erklaerung = "Der Versatz im Indexausdruck wurde ignoriert, es wurde auf den Index ohne Rechnung zugegriffen.";

        if (array.get().typ() == JType.STRING_ARRAY) {
            String[] a = array.get().asStringArray();
            return (i < 0 || i >= a.length)
                    ? Optional.empty()
                    : Optional.of(new Treffer("B05", Value.ofString(a[i]), erklaerung));
        }
        if (array.get().typ() == JType.INT_ARRAY) {
            int[] a = array.get().asIntArray();
            return (i < 0 || i >= a.length)
                    ? Optional.empty()
                    : Optional.of(new Treffer("B05", Value.ofInt(a[i]), erklaerung));
        }
        return Optional.empty();
    }

    // ================================================================
    // B06: ein Glied der Methodenkette uebersehen
    // ================================================================

    /**
     * Bei einer Kette wie {@code "HEY".substring(1).toLowerCase()} wendet die Lernende nur
     * einen der beiden Aufrufe an und uebersieht den anderen.
     *
     * <p>Die naheliegendere Annahme, naemlich eine vertauschte Reihenfolge, waere hier
     * nicht diagnostisch: Bei substring und toLowerCase liefert die vertauschte Reihenfolge
     * genau dasselbe Ergebnis, die beiden Aufrufe sind in diesem Fall vertauschbar. Ein
     * ausgelassenes Glied dagegen ist eindeutig beobachtbar.</p>
     */
    private List<Treffer> b06KetteUnvollstaendig(Expr wurzel, EvaluationContext ctx) {
        List<Treffer> treffer = new ArrayList<>();
        if (!(wurzel instanceof Expr.Call aussen) || !(aussen.empfaenger() instanceof Expr.Call innen)) {
            return treffer;
        }
        Optional<Value> basis = werteBlatt(innen.empfaenger(), ctx);
        if (basis.isEmpty()) {
            return treffer;
        }
        List<Value> argumenteInnen = werteArgumente(innen.argumente(), ctx);
        List<Value> argumenteAussen = werteArgumente(aussen.argumente(), ctx);
        if (argumenteInnen == null || argumenteAussen == null) {
            return treffer;
        }

        wendeAn(basis.get(), innen.methode(), argumenteInnen).ifPresent(wert ->
                treffer.add(new Treffer("B06", wert,
                        "Nur " + innen.methode() + "(...) angewendet, " + aussen.methode() + "(...) uebersehen.")));

        wendeAn(basis.get(), aussen.methode(), argumenteAussen).ifPresent(wert ->
                treffer.add(new Treffer("B06", wert,
                        "Nur " + aussen.methode() + "(...) angewendet, " + innen.methode() + "(...) uebersehen.")));

        return treffer;
    }

    // ================================================================
    // B10: Modulo als Quotient gelesen
    // ================================================================

    private Optional<Treffer> b10ModuloAlsQuotient(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Bin b) || !b.op().equals("%")) {
            return Optional.empty();
        }
        Optional<Value> links = werteBlatt(b.links(), ctx);
        Optional<Value> rechts = werteBlatt(b.rechts(), ctx);
        if (links.isEmpty() || rechts.isEmpty()
                || links.get().typ() != JType.INT || rechts.get().typ() != JType.INT
                || rechts.get().asInt() == 0) {
            return Optional.empty();
        }
        return Optional.of(new Treffer("B10", Value.ofInt(links.get().asInt() / rechts.get().asInt()),
                "Der Quotient wurde berechnet statt des Restes."));
    }

    // ================================================================
    // B11: falscher Zweig des ternaeren Operators
    // ================================================================

    private Optional<Treffer> b11FalscherZweig(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.Ternary t)) {
            return Optional.empty();
        }
        Optional<Value> bedingung = werteBlatt(t.bedingung(), ctx);
        if (bedingung.isEmpty() || !(bedingung.get().wert() instanceof Boolean wahr)) {
            return Optional.empty();
        }
        Expr falscherZweig = wahr ? t.sonst() : t.dann();
        return werteBlatt(falscherZweig, ctx).map(wert -> new Treffer("B11", wert,
                "Die Bedingung wurde falsch ausgewertet, dadurch wurde der andere Zweig genommen."));
    }

    // ================================================================
    // B12: Praefix und Postfix verwechselt
    // ================================================================

    /**
     * Bei {@code ++x} wird der alte Wert angegeben, bei {@code x++} der neue.
     *
     * <p>Wichtig: Diese Regel liest die Variable <em>nach</em> der Auswertung des
     * Ausdrucks, denn zu diesem Zeitpunkt hat der Evaluator sie bereits veraendert. Aus
     * dem gespeicherten Wert und der Form des Operators laesst sich der urspruengliche
     * Wert zurueckrechnen. Wird der Simulator ohne vorherige Auswertung aufgerufen, ist
     * das Ergebnis dieser Regel nicht sinnvoll.</p>
     */
    private Optional<Treffer> b12PraefixPostfixVerwechselt(Expr wurzel, EvaluationContext ctx) {
        if (!(wurzel instanceof Expr.IncDec d)) {
            return Optional.empty();
        }
        Optional<Value> gespeichert = werteBlatt(d.ziel(), ctx);
        if (gespeichert.isEmpty() || gespeichert.get().typ() != JType.INT) {
            return Optional.empty();
        }
        int aktuell = gespeichert.get().asInt();
        int schritt = d.op().equals("++") ? -1 : 1;
        int buggy = d.prefix() ? aktuell + schritt : aktuell;

        String erklaerung = d.prefix()
                ? "Das Praefix " + d.op() + " wie ein Postfix gelesen, also der alte Wert angegeben."
                : "Das Postfix " + d.op() + " wie ein Praefix gelesen, also der neue Wert angegeben.";
        return Optional.of(new Treffer("B12", Value.ofInt(buggy), erklaerung));
    }

    // ================================================================
    // B14: Referenzvergleich und Inhaltsvergleich verwechselt
    // ================================================================

    /**
     * Deckt beide Richtungen ab. Bei {@code s.equals("wort")} nimmt die Lernende an, es
     * werde die Referenz verglichen, und antwortet mit false, obwohl der Inhalt gleich
     * ist. Bei {@code s == "wort"} nimmt sie umgekehrt einen Inhaltsvergleich an.
     *
     * <p>Der erste Fall wird nur gemeldet, wenn die Inhalte tatsaechlich gleich sind.
     * Sind sie verschieden, liefern beide Deutungen false, und der Fehler waere nicht von
     * der richtigen Antwort zu unterscheiden.</p>
     */
    private List<Treffer> b14ReferenzStattInhalt(Expr wurzel, EvaluationContext ctx) {
        List<Treffer> treffer = new ArrayList<>();

        if (wurzel instanceof Expr.Call c && c.methode().equals("equals") && c.argumente().size() == 1) {
            Optional<Value> empfaenger = werteBlatt(c.empfaenger(), ctx);
            Optional<Value> argument = werteBlatt(c.argumente().get(0), ctx);
            if (empfaenger.isPresent() && argument.isPresent()
                    && empfaenger.get().typ() == JType.STRING
                    && argument.get().typ() == JType.STRING
                    && empfaenger.get().asString().equals(argument.get().asString())) {
                treffer.add(new Treffer("B14", Value.ofBool(false),
                        "equals als Referenzvergleich gedeutet: Der Inhalt stimmt ueberein, "
                                + "die Antwort lautet aber false."));
            }
        }

        if (wurzel instanceof Expr.Bin b && (b.op().equals("==") || b.op().equals("!="))) {
            Optional<Value> links = werteBlatt(b.links(), ctx);
            Optional<Value> rechts = werteBlatt(b.rechts(), ctx);
            if (links.isPresent() && rechts.isPresent()
                    && links.get().typ() == JType.STRING && rechts.get().typ() == JType.STRING) {
                boolean inhaltGleich = links.get().asString().equals(rechts.get().asString());
                boolean ergebnis = b.op().equals("==") == inhaltGleich;
                treffer.add(new Treffer("B14", Value.ofBool(ergebnis),
                        "Der Operator " + b.op() + " wurde auf Strings als Inhaltsvergleich gedeutet."));
            }
        }

        return treffer;
    }

    // ================================================================
    // Hilfsmittel
    // ================================================================

    /** Wertet einen Teilausdruck aus; leer, wenn er nicht auswertbar ist. */
    private Optional<Value> werteBlatt(Expr e, EvaluationContext ctx) {
        EvaluationResult r = evaluator.evaluate(e, ctx);
        return r.auswertbar() ? Optional.of(r.wert()) : Optional.empty();
    }

    /** Wertet alle Argumente aus; liefert null, sobald eines nicht auswertbar ist. */
    private List<Value> werteArgumente(List<Expr> argumente, EvaluationContext ctx) {
        List<Value> werte = new ArrayList<>();
        for (Expr argument : argumente) {
            Optional<Value> wert = werteBlatt(argument, ctx);
            if (wert.isEmpty()) {
                return null;
            }
            werte.add(wert.get());
        }
        return werte;
    }

    /**
     * Wendet einen String-Methodenaufruf auf einen Wert an, oder liefert ein leeres
     * Optional, wenn das so nicht moeglich ist.
     *
     * <p>Bewusst eine eigene kleine Umsetzung: Der Simulator arbeitet auf einzelnen
     * Teilausdruecken und soll dabei weder das Schrittprotokoll fuellen noch eine
     * Ausnahme ausloesen.</p>
     */
    private Optional<Value> wendeAn(Value empfaenger, String methode, List<Value> argumente) {
        if (empfaenger.typ() != JType.STRING) {
            return Optional.empty();
        }
        String s = empfaenger.asString();
        try {
            return switch (methode) {
                case "length" -> Optional.of(Value.ofInt(s.length()));
                case "toLowerCase" -> Optional.of(Value.ofString(s.toLowerCase()));
                case "toUpperCase" -> Optional.of(Value.ofString(s.toUpperCase()));
                case "trim" -> Optional.of(Value.ofString(s.trim()));
                case "charAt" -> {
                    int i = argumente.get(0).alsGanzzahl();
                    yield (i < 0 || i >= s.length())
                            ? Optional.<Value>empty()
                            : Optional.of(Value.ofChar(s.charAt(i)));
                }
                case "substring" -> {
                    int von = argumente.get(0).alsGanzzahl();
                    int bis = argumente.size() > 1 ? argumente.get(1).alsGanzzahl() : s.length();
                    yield (von < 0 || bis > s.length() || von > bis)
                            ? Optional.<Value>empty()
                            : Optional.of(Value.ofString(s.substring(von, bis)));
                }
                case "equals" -> Optional.of(Value.ofBool(
                        !argumente.isEmpty()
                                && argumente.get(0).typ() == JType.STRING
                                && s.equals(argumente.get(0).asString())));
                default -> Optional.<Value>empty();
            };
        } catch (RuntimeException nichtAnwendbar) {
            return Optional.empty();
        }
    }
}