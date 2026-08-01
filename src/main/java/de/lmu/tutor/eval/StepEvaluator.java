package de.lmu.tutor.eval;

import java.util.ArrayList;
import java.util.List;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;

/**
 * Wertet einen Ausdrucksbaum "von innen nach aussen" aus und liefert Wert, Datentyp
 * und ein Schrittprotokoll. Nicht auswertbare Ausdruecke (Typfehler, unbekannte Variable,
 * Division durch Null, Index ausserhalb der Grenzen) werden als solche gekennzeichnet.
 *
 * <p>Die Semantik bildet die Regeln der Bug Library ab: Ganzzahldivision, String-Konkatenation,
 * Cast-Truncation, Kurzschlussauswertung, Modulo, ternaerer Operator, Inkrement usw.</p>
 */
public final class StepEvaluator {

    /** Wertet {@code ausdruck} unter der Belegung {@code ctx} aus. */
    public EvaluationResult evaluate(Expr ausdruck, EvaluationContext ctx) {
        List<EvaluationStep> schritte = new ArrayList<>();
        try {
            Value wert = eval(ausdruck, ctx, schritte);
            return EvaluationResult.of(wert, schritte);
        } catch (NotEvaluableException e) {
            return EvaluationResult.nichtAuswertbar(e.getMessage(), schritte);
        }
    }

    // Rekursive Auswertung. Fuer zusammengesetzte Knoten wird ein Schritt protokolliert.
    private Value eval(Expr e, EvaluationContext ctx, List<EvaluationStep> schritte) {
        return switch (e) {
            case Expr.Lit l -> new Value(l.typ(), l.wert());
            case Expr.Var v -> {
                if (!ctx.kennt(v.name())) {
                    throw new NotEvaluableException("Variable '" + v.name() + "' ist nicht definiert");
                }
                yield ctx.hole(v.name());
            }
            case Expr.Bin b -> protokolliere(b, evalBin(b, ctx, schritte), schritte);
            case Expr.Unary u -> protokolliere(u, evalUnary(u, ctx, schritte), schritte);
            case Expr.Cast c -> protokolliere(c, evalCast(c, ctx, schritte), schritte);
            case Expr.Index i -> protokolliere(i, evalIndex(i, ctx, schritte), schritte);
            case Expr.Call c -> protokolliere(c, evalCall(c, ctx, schritte), schritte);
            case Expr.Ternary t -> protokolliere(t, evalTernary(t, ctx, schritte), schritte);
            case Expr.IncDec d -> protokolliere(d, evalIncDec(d, ctx, schritte), schritte);
        };
    }

    private Value protokolliere(Expr e, Value ergebnis, List<EvaluationStep> schritte) {
        schritte.add(new EvaluationStep(e.render(), ergebnis.mitTyp()));
        return ergebnis;
    }

    // ---- Binaere Operatoren ----
    private Value evalBin(Expr.Bin b, EvaluationContext ctx, List<EvaluationStep> schritte) {
        String op = b.op();

        // Kurzschlussauswertung: rechten Operanden ggf. gar nicht auswerten
        if (op.equals("&&") || op.equals("||")) {
            Value links = eval(b.links(), ctx, schritte);
            if (links.typ() != JType.BOOL) throw new NotEvaluableException(op + " erwartet boolean links");
            boolean l = links.asBool();
            if (op.equals("&&") && !l) return Value.ofBool(false);   // steht schon fest
            if (op.equals("||") && l) return Value.ofBool(true);     // steht schon fest
            Value rechts = eval(b.rechts(), ctx, schritte);
            if (rechts.typ() != JType.BOOL) throw new NotEvaluableException(op + " erwartet boolean rechts");
            return Value.ofBool(rechts.asBool());
        }

        Value l = eval(b.links(), ctx, schritte);
        Value r = eval(b.rechts(), ctx, schritte);

        // String-Konkatenation: + mit mindestens einem String
        if (op.equals("+") && (l.typ() == JType.STRING || r.typ() == JType.STRING)) {
            return Value.ofString(alsText(l) + alsText(r));
        }

        return switch (op) {
            case "+", "-", "*", "/", "%" -> arithmetik(op, l, r);
            case "<", ">", "<=", ">=" -> vergleich(op, l, r);
            case "==", "!=" -> gleichheit(op, l, r);
            case "&" -> {
                if (l.typ() != JType.BOOL || r.typ() != JType.BOOL)
                    throw new NotEvaluableException("& erwartet boolean auf beiden Seiten");
                yield Value.ofBool(l.asBool() & r.asBool());
            }
            default -> throw new NotEvaluableException("Unbekannter Operator: " + op);
        };
    }

    private Value arithmetik(String op, Value l, Value r) {
        if (!l.typ().isNumeric() || !r.typ().isNumeric()) {
            throw new NotEvaluableException(op + " ist fuer " + l.typ().javaName()
                    + " und " + r.typ().javaName() + " nicht definiert");
        }
        boolean doppelt = l.typ() == JType.DOUBLE || r.typ() == JType.DOUBLE;
        if (doppelt) {
            double a = l.alsZahl(), c = r.alsZahl();
            return Value.ofDouble(switch (op) {
                case "+" -> a + c; case "-" -> a - c; case "*" -> a * c;
                case "/" -> a / c; case "%" -> a % c; default -> 0;
            });
        }
        int a = l.alsGanzzahl(), c = r.alsGanzzahl();
        if ((op.equals("/") || op.equals("%")) && c == 0) {
            throw new NotEvaluableException("Division durch Null");
        }
        return Value.ofInt(switch (op) {
            case "+" -> a + c; case "-" -> a - c; case "*" -> a * c;
            case "/" -> a / c; case "%" -> a % c; default -> 0;
        });
    }

    private Value vergleich(String op, Value l, Value r) {
        if (!l.typ().isNumeric() || !r.typ().isNumeric()) {
            throw new NotEvaluableException(op + " ist fuer diese Typen nicht definiert");
        }
        double a = l.alsZahl(), c = r.alsZahl();
        return Value.ofBool(switch (op) {
            case "<" -> a < c; case ">" -> a > c; case "<=" -> a <= c; case ">=" -> a >= c;
            default -> false;
        });
    }

    private Value gleichheit(String op, Value l, Value r) {
        boolean gleich;
        if (l.typ().isNumeric() && r.typ().isNumeric()) {
            gleich = l.alsZahl() == r.alsZahl();
        } else if (l.typ() == JType.BOOL && r.typ() == JType.BOOL) {
            gleich = l.asBool() == r.asBool();
        } else if (l.typ() == JType.STRING && r.typ() == JType.STRING) {
            gleich = l.asString().equals(r.asString());
        } else {
            throw new NotEvaluableException(op + " ist fuer " + l.typ().javaName()
                    + " und " + r.typ().javaName() + " nicht definiert");
        }
        return Value.ofBool(op.equals("==") ? gleich : !gleich);
    }

    // ---- Unaere Operatoren ----
    private Value evalUnary(Expr.Unary u, EvaluationContext ctx, List<EvaluationStep> schritte) {
        Value o = eval(u.operand(), ctx, schritte);
        return switch (u.op()) {
            case "!" -> {
                if (o.typ() != JType.BOOL) throw new NotEvaluableException("! erwartet boolean");
                yield Value.ofBool(!o.asBool());
            }
            case "-" -> {
                if (!o.typ().isNumeric()) throw new NotEvaluableException("unaeres - erwartet eine Zahl");
                yield o.typ() == JType.DOUBLE ? Value.ofDouble(-o.alsZahl()) : Value.ofInt(-o.alsGanzzahl());
            }
            default -> throw new NotEvaluableException("Unbekannter unaerer Operator: " + u.op());
        };
    }

    // ---- Cast ----
    private Value evalCast(Expr.Cast c, EvaluationContext ctx, List<EvaluationStep> schritte) {
        Value o = eval(c.operand(), ctx, schritte);
        return switch (c.zielTyp()) {
            case INT -> {
                if (!o.typ().isNumeric()) throw new NotEvaluableException("(int) ist fuer " + o.typ().javaName() + " nicht zulaessig");
                yield Value.ofInt((int) o.alsZahl());   // schneidet ab (Truncation)
            }
            case DOUBLE -> {
                if (!o.typ().isNumeric()) throw new NotEvaluableException("(double) ist fuer " + o.typ().javaName() + " nicht zulaessig");
                yield Value.ofDouble(o.alsZahl());
            }
            case CHAR -> {
                if (o.typ() != JType.INT && o.typ() != JType.CHAR)
                    throw new NotEvaluableException("(char) ist fuer " + o.typ().javaName() + " nicht zulaessig");
                yield Value.ofChar((char) o.alsGanzzahl());
            }
            default -> throw new NotEvaluableException("Cast nach " + c.zielTyp().javaName() + " nicht unterstuetzt");
        };
    }

    // ---- Array-Zugriff ----
    private Value evalIndex(Expr.Index ix, EvaluationContext ctx, List<EvaluationStep> schritte) {
        Value arr = eval(ix.array(), ctx, schritte);
        Value idx = eval(ix.indexAusdruck(), ctx, schritte);
        if (!arr.typ().isArray()) throw new NotEvaluableException("Kein Array: " + arr.typ().javaName());
        if (idx.typ() != JType.INT && idx.typ() != JType.CHAR)
            throw new NotEvaluableException("Array-Index muss ganzzahlig sein");
        int i = idx.alsGanzzahl();
        if (arr.typ() == JType.INT_ARRAY) {
            int[] a = arr.asIntArray();
            if (i < 0 || i >= a.length) throw new NotEvaluableException("Index " + i + " ausserhalb der Grenzen (Laenge " + a.length + ")");
            return Value.ofInt(a[i]);
        } else {
            String[] a = arr.asStringArray();
            if (i < 0 || i >= a.length) throw new NotEvaluableException("Index " + i + " ausserhalb der Grenzen (Laenge " + a.length + ")");
            return Value.ofString(a[i]);
        }
    }

    // ---- Methodenaufrufe (String-Methoden) ----
    private Value evalCall(Expr.Call c, EvaluationContext ctx, List<EvaluationStep> schritte) {
        Value empf = eval(c.empfaenger(), ctx, schritte);
        List<Value> args = new ArrayList<>();
        for (Expr a : c.argumente()) args.add(eval(a, ctx, schritte));

        if (empf.typ() == JType.STRING) {
            String s = empf.asString();
            return switch (c.methode()) {
                case "length" -> Value.ofInt(s.length());
                case "toLowerCase" -> Value.ofString(s.toLowerCase());
                case "toUpperCase" -> Value.ofString(s.toUpperCase());
                case "trim" -> Value.ofString(s.trim());
                case "charAt" -> {
                    int i = args.get(0).alsGanzzahl();
                    if (i < 0 || i >= s.length()) throw new NotEvaluableException("charAt: Index " + i + " ausserhalb der Grenzen");
                    yield Value.ofChar(s.charAt(i));
                }
                case "substring" -> {
                    int von = args.get(0).alsGanzzahl();
                    int bis = args.size() > 1 ? args.get(1).alsGanzzahl() : s.length();
                    if (von < 0 || bis > s.length() || von > bis) throw new NotEvaluableException("substring: ungueltige Grenzen");
                    yield Value.ofString(s.substring(von, bis));
                }
                case "equals" -> Value.ofBool(args.get(0).typ() == JType.STRING && s.equals(args.get(0).asString()));
                default -> throw new NotEvaluableException("Methode " + c.methode() + " auf String nicht unterstuetzt");
            };
        }
        throw new NotEvaluableException("Methode " + c.methode() + " auf " + empf.typ().javaName() + " nicht definiert");
    }

    // ---- Ternaerer Operator: nur der gewaehlte Zweig wird ausgewertet ----
    private Value evalTernary(Expr.Ternary t, EvaluationContext ctx, List<EvaluationStep> schritte) {
        Value bed = eval(t.bedingung(), ctx, schritte);
        if (bed.typ() != JType.BOOL) throw new NotEvaluableException("Bedingung des ? : muss boolean sein");
        return bed.asBool() ? eval(t.dann(), ctx, schritte) : eval(t.sonst(), ctx, schritte);
    }

    // ---- Inkrement/Dekrement mit Seiteneffekt ----
    private Value evalIncDec(Expr.IncDec d, EvaluationContext ctx, List<EvaluationStep> schritte) {
        if (!(d.ziel() instanceof Expr.Var v)) throw new NotEvaluableException("++/-- erwartet eine Variable");
        if (!ctx.kennt(v.name())) throw new NotEvaluableException("Variable '" + v.name() + "' ist nicht definiert");
        Value alt = ctx.hole(v.name());
        if (alt.typ() != JType.INT) throw new NotEvaluableException("++/-- erwartet int");
        int neu = d.op().equals("++") ? alt.asInt() + 1 : alt.asInt() - 1;
        ctx.setzeInt(v.name(), neu);                       // Seiteneffekt
        return Value.ofInt(d.prefix() ? neu : alt.asInt()); // Praefix: neuer Wert, Postfix: alter Wert
    }

    // Java-nahe Textform fuer die String-Konkatenation
    private static String alsText(Value v) {
        return switch (v.typ()) {
            case STRING -> v.asString();
            case CHAR -> String.valueOf(v.asChar());
            case INT -> Integer.toString(v.asInt());
            case DOUBLE -> Double.toString(v.asDouble());
            case BOOL -> Boolean.toString(v.asBool());
            default -> String.valueOf(v.wert());
        };
    }
}