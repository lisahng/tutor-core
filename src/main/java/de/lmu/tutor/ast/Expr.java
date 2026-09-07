package de.lmu.tutor.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Java-Ausdruck als Baum (abstrakter Syntaxbaum, AST).
 *
 * <p>{@code Expr} ist eine <em>sealed interface</em>: Es gibt genau die unten
 * definierten Knotentypen und keine anderen. Jeder Knoten kann sich als
 * Java-Quelltext ausgeben ({@link #render()}) und seine Kinder auflisten
 * ({@link #children()}). Aus den Kindern ergeben sich Groesse und Hoehe des
 * Baums automatisch.</p>
 *
 * <p>Der Baum bildet die Auswertungsreihenfolge (Operatorpraezedenz) strukturell
 * ab: Ein Knoten steht ueber seinen Operanden, d. h. seine Kinder werden zuerst
 * berechnet. Das nutzt in Schritt 3 der Evaluator.</p>
 */
public sealed interface Expr {

    /** Erzeugt den Java-Quelltext dieses Ausdrucks, mit den noetigen Klammern. */
    String render();

    /** Die direkten Kinder dieses Knotens (fuer die Traversierung des Baums). */
    List<Expr> children();

    /** Bindungsstaerke: hoeher = bindet staerker. Steuert die Klammersetzung im render(). */
    int precedence();

    /** Anzahl aller Knoten im Teilbaum: dieser Knoten plus alle Nachfahren. */
    default int groesse() {
        int summe = 1;
        for (Expr kind : children()) {
            summe += kind.groesse();
        }
        return summe;
    }

    /** Hoehe des Teilbaums (ein Blatt hat Hoehe 1). */
    default int hoehe() {
        int max = 0;
        for (Expr kind : children()) {
            max = Math.max(max, kind.hoehe());
        }
        return max + 1;
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden fuer die Klammersetzung beim render()
    // ------------------------------------------------------------------

    /** Klammert das Kind, wenn es schwaecher bindet als der Elternknoten. */
    static String klammere(Expr kind, int elternPrecedence) {
        return kind.precedence() < elternPrecedence
                ? "(" + kind.render() + ")"
                : kind.render();
    }

    /**
     * Fuer den rechten Operanden eines links-assoziativen Operators:
     * hier wird auch bei gleicher Bindungsstaerke geklammert
     * (z. B. a - (b - c), damit die Struktur erhalten bleibt).
     */
    static String klammereRechts(Expr kind, int elternPrecedence) {
        return kind.precedence() <= elternPrecedence
                ? "(" + kind.render() + ")"
                : kind.render();
    }

    /** Bindungsstaerke eines binaeren Operators. */
    static int precedenceOf(String op) {
        return switch (op) {
            case "*", "/", "%" -> 70;
            case "+", "-" -> 60;
            case "<", ">", "<=", ">=" -> 50;
            case "==", "!=" -> 45;
            case "&" -> 40;
            case "&&" -> 35;
            case "||" -> 30;
            default -> throw new IllegalArgumentException("Unbekannter Operator: " + op);
        };
    }

    // ==================================================================
    // Die Knotentypen
    // ==================================================================

    /** Ein konstanter Wert, z. B. 5, 2.1, 'J', true oder "HEY". */
    record Lit(Object wert, JType typ) implements Expr {
        @Override
        public String render() {
            return switch (typ) {
                case STRING -> "\"" + wert + "\"";
                case CHAR -> "'" + wert + "'";
                default -> String.valueOf(wert);
            };
        }
    @Override
        public List<Expr> children() { return List.of(); }
        public int precedence() { return 100; }
    }

    /** Eine Variable, z. B. k oder a. */
    record Var(String name) implements Expr {
        public String render() { return name; }
        public List<Expr> children() { return List.of(); }
        public int precedence() { return 100; }
    }

    /** Ein binaerer Operator mit linkem und rechtem Operanden, z. B. 3 + 4. */
    record Bin(String op, Expr links, Expr rechts) implements Expr {
        public String render() {
            return Expr.klammere(links, precedence()) + " " + op + " "
                    + Expr.klammereRechts(rechts, precedence());
        }
        public List<Expr> children() { return List.of(links, rechts); }
        public int precedence() { return Expr.precedenceOf(op); }
    }

    /** Ein unaerer Operator vor seinem Operanden: ! oder unaeres -. */
    record Unary(String op, Expr operand) implements Expr {
        public String render() { return op + Expr.klammere(operand, precedence()); }
        public List<Expr> children() { return List.of(operand); }
        public int precedence() { return 80; }
    }

    /** Ein expliziter Cast, z. B. (int) 2.1. */
    record Cast(JType zielTyp, Expr operand) implements Expr {
        public String render() {
            return "(" + zielTyp.javaName() + ") " + Expr.klammere(operand, precedence());
        }
        public List<Expr> children() { return List.of(operand); }
        public int precedence() { return 80; }
    }

    /** Ein Array-Zugriff, z. B. a[k + 1]. */
    record Index(Expr array, Expr indexAusdruck) implements Expr {
        public String render() {
            return Expr.klammere(array, precedence()) + "[" + indexAusdruck.render() + "]";
        }
        public List<Expr> children() { return List.of(array, indexAusdruck); }
        public int precedence() { return 90; }
    }

    /** Ein Methodenaufruf, z. B. s.substring(1) oder a[0].length(). */
    record Call(Expr empfaenger, String methode, List<Expr> argumente) implements Expr {
        public String render() {
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < argumente.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(argumente.get(i).render());
            }
            return Expr.klammere(empfaenger, precedence()) + "." + methode + "(" + args + ")";
        }
        public List<Expr> children() {
            List<Expr> kinder = new ArrayList<>();
            kinder.add(empfaenger);
            kinder.addAll(argumente);
            return List.copyOf(kinder);
        }
        public int precedence() { return 90; }
    }

    /**
     * Ein statischer Methodenaufruf auf einer Klasse, z. B. Double.parseDouble(a[3])
     * oder Math.sqrt(x). Anders als {@link Call} gibt es hier keinen Empfaenger-Ausdruck,
     * sondern nur einen Klassennamen (er wird nicht als Variable im Kontext nachgeschlagen).
     */
    record StaticCall(String klasse, String methode, List<Expr> argumente) implements Expr {
        public String render() {
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < argumente.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(argumente.get(i).render());
            }
            return klasse + "." + methode + "(" + args + ")";
        }
        public List<Expr> children() { return List.copyOf(argumente); }
        public int precedence() { return 90; }
    }

    /** Der ternaere Operator: bedingung ? dann : sonst. */
    record Ternary(Expr bedingung, Expr dann, Expr sonst) implements Expr {
        public String render() {
            int p = precedence() + 1;
            return Expr.klammere(bedingung, p) + " ? "
                    + Expr.klammere(dann, p) + " : "
                    + Expr.klammere(sonst, p);
        }
        public List<Expr> children() { return List.of(bedingung, dann, sonst); }
        public int precedence() { return 20; }
    }

    /** Inkrement/Dekrement, Praefix (++x) oder Postfix (x++). */
    record IncDec(String op, boolean prefix, Expr ziel) implements Expr {
        public String render() {
            String z = Expr.klammere(ziel, precedence());
            return prefix ? op + z : z + op;
        }
        public List<Expr> children() { return List.of(ziel); }
        public int precedence() { return 85; }
    }
}