package de.lmu.tutor.demo;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.staticCall;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlerklassifikator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Zeigt Schritt 5: Vergleichsmodul und Fehlerklassifikator.
 *
 * <p>Zu jeder Zeile wird eine typische Fehleingabe gestellt und ausgegeben, welche
 * Bug-Kategorie das System daraus ableitet. Die Konfidenz ist dabei aussagekraeftiger als
 * die Kategorie selbst:</p>
 *
 * <ul>
 *   <li>{@code EXAKT} - die Signatur der Antwort laesst nur eine Deutung zu, etwa ein
 *       richtiger Wert mit falschem Datentyp.</li>
 *   <li>{@code SIMULIERT} - eine bekannte Fehlregel wurde auf den Ausdruck angewendet und
 *       hat genau den eingegebenen Wert reproduziert. Das ist die staerkste Form, weil sie
 *       den Fehler nachrechnen kann.</li>
 *   <li>{@code ZIELKATEGORIE} - keine Regel passt, aber die Aufgabe wurde fuer diese
 *       Kategorie erzeugt. Eine Vermutung, keine Erklaerung.</li>
 *   <li>{@code UNBEKANNT} - nichts passt. Diese Faelle zeigen Luecken in der Bug Library.</li>
 * </ul>
 *
 * <p>Die IDs folgen der Nummerierung aus Anhang A.1 der Arbeit.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.DiagnoseDemo"  </pre>
 */
public final class DiagnoseDemo {

    private static final StepEvaluator EVALUATOR = new StepEvaluator();
    private static final BugLibrary LIB = BugLibrary.loadDefault();
    private static final Fehlerklassifikator KLASSIFIKATOR = new Fehlerklassifikator(LIB);

    private static final EvaluationContext LEER = new EvaluationContext();

    public static void main(String[] args) {
        System.out.println("Fehlerklassifikator - Demo (Schritt 5)");
        System.out.println("=".repeat(74));
        System.out.println();

        exakteSignaturen();
        simulierteFehlregeln();
        fehlerkombinationen();
        rueckfallstufen();
        klausuraufgabe();
    }

    // ---- Stufe 1 ----

    private static void exakteSignaturen() {
        System.out.println("-- Exakte Signaturen ------------------------------------------");

        // Wert richtig, nur der Datentyp falsch
        zeige("5 / 2 als double", aufgabe("B02", bin("/", intLit(5), intLit(2))),
                NutzerAntwort.wert(Value.ofDouble(2.0)));

        // Nicht auswertbarer Ausdruck mit einem Wert beantwortet
        zeige("5 + true beantwortet", aufgabe("B08", bin("+", intLit(5), boolLit(true))),
                NutzerAntwort.wert(Value.ofInt(6)));

        // Richtiges Zeichen, aber als String statt als char
        zeige("charAt als String", aufgabe("B13", call(stringLit("Java"), "charAt", intLit(0))),
                NutzerAntwort.wert(Value.ofString("J")));
    }

    // ---- Stufe 2, einzelne Regeln ----

    private static void simulierteFehlregeln() {
        System.out.println("-- Simulierte Fehlregeln --------------------------------------");

        // Links nach rechts gerechnet statt Punkt vor Strich
        zeige("(3 + 4) * 2", aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(14)));

        // Fliesskommadivision statt Ganzzahldivision
        zeige("20 / 3 als Kommazahl", aufgabe("B02", bin("/", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofDouble(20 / 3.0)));

        // Zahlenaehnlichen String addiert statt verkettet
        zeige("\"17\" + 4 addiert", aufgabe("B03", bin("+", stringLit("17"), intLit(4))),
                NutzerAntwort.wert(Value.ofInt(21)));

        // Cast als Rundung gelesen
        zeige("(int) 2.7 gerundet", aufgabe("B04", cast(JType.INT, doubleLit(2.7))),
                NutzerAntwort.wert(Value.ofInt(3)));

        // Versatz im Indexausdruck ignoriert
        EvaluationContext arrayKontext = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 1);
        zeige("a[k + 1] als a[k]",
                new Aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), arrayKontext,
                        "a = {rot, gelb, blau, gruen}, k = 1"),
                NutzerAntwort.wert(Value.ofString("gelb")));

        // Ein Glied der Methodenkette uebersehen
        zeige("substring ohne toLowerCase",
                aufgabe("B06", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase")),
                NutzerAntwort.wert(Value.ofString("EY")));

        // Modulo als Quotient gelesen
        zeige("20 % 3 als Quotient", aufgabe("B10", bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(6)));

        // Falscher Zweig des ternaeren Operators
        EvaluationContext ternaerKontext = new EvaluationContext().setzeInt("x", -3);
        zeige("falscher Zweig",
                new Aufgabe("B11", ternary(bin(">", var("x"), intLit(0)), intLit(4), intLit(7)),
                        ternaerKontext, "x = -3"),
                NutzerAntwort.wert(Value.ofInt(4)));

        // equals als Referenzvergleich gedeutet
        zeige("equals liefert false",
                aufgabe("B14", call(stringLit("haus"), "equals", stringLit("haus"))),
                NutzerAntwort.wert(Value.ofBool(false)));
    }

    // ---- Stufe 2, Kombinationen ----

    /**
     * Zwei Fehlvorstellungen, die zusammenkommen.
     *
     * <p>Wer die Praezedenz missachtet, rechnet von links nach rechts und wendet dabei
     * zugleich seine eigene Vorstellung von Division oder Modulo an. Die Zwischenwerte
     * zeigen, dass sich die drei Ergebnisse unterscheiden, dass also die Kombination
     * tatsaechlich diagnostisch ist und nicht mit einer einzelnen Regel zusammenfaellt.</p>
     *
     * <p>Das System meldet eine Kombination nur dann, wenn keine einzelne Regel den Wert
     * trifft. Eine Annahme ist immer wahrscheinlicher als zwei gleichzeitige.</p>
     */
    private static void fehlerkombinationen() {
        System.out.println("-- Fehlerkombinationen ----------------------------------------");

        Expr mitDivision = bin("+", intLit(12), bin("/", intLit(9), intLit(2)));
        System.out.println("12 + 9 / 2:  korrekt 16, links nach rechts 10, "
                + "mit Fliesskommadivision 10.5");
        System.out.println();
        zeige("nur Praezedenz falsch", aufgabe("B01", mitDivision),
                NutzerAntwort.wert(Value.ofInt(10)));
        zeige("Praezedenz und Division", aufgabe("B01", mitDivision),
                NutzerAntwort.wert(Value.ofDouble(10.5)));

        Expr mitModulo = bin("+", intLit(7), bin("%", intLit(20), intLit(6)));
        System.out.println("7 + 20 % 6:  korrekt 9, links nach rechts 3, "
                + "mit Modulo als Division 4");
        System.out.println();
        zeige("nur Praezedenz falsch", aufgabe("B01", mitModulo),
                NutzerAntwort.wert(Value.ofInt(3)));
        zeige("Praezedenz und Modulo", aufgabe("B01", mitModulo),
                NutzerAntwort.wert(Value.ofInt(4)));
    }

    // ---- Stufe 3 ----

    private static void rueckfallstufen() {
        System.out.println("-- Rueckfallstufen --------------------------------------------");

        // Kein Fehlermuster erklaert diesen Wert, es bleibt die Herkunft der Aufgabe
        zeige("unerklaerbarer Wert",
                aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(99)));

        // Ohne Zielkategorie bleibt nur die Struktur des Ausdrucks
        zeige("ohne Zielkategorie", aufgabe(null, bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(99)));

        // Zum Vergleich eine richtige Antwort
        zeige("richtig geloest",
                aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(11)));
    }

    // ---- Klausuraufgabe 1a ----

    private static void klausuraufgabe() {
        System.out.println("-- Klausuraufgabe 1a ------------------------------------------");

        EvaluationContext klausurKontext = new EvaluationContext()
                .setzeStringArray("a", new String[]{"HEY", "x", "131", "2.1", "eip"});
        Expr klausur1a = cast(JType.INT, staticCall("Double", "parseDouble", index(var("a"), intLit(3))));
        zeige("Cast rundet statt abzuschneiden",
                new Aufgabe("B04", klausur1a, klausurKontext, "a = {HEY, x, 131, 2.1, eip}"),
                NutzerAntwort.wert(Value.ofDouble(2.1)));
    }

    // ---- Hilfsmittel ----

    private static Aufgabe aufgabe(String kategorieId, Expr ausdruck) {
        return new Aufgabe(kategorieId, ausdruck, LEER, "");
    }

    private static void zeige(String titel, Aufgabe aufgabe, NutzerAntwort antwort) {
        EvaluationResult referenz = EVALUATOR.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
        Diagnose diagnose = KLASSIFIKATOR.diagnostiziere(aufgabe, referenz, antwort);

        System.out.println(titel + ":");
        System.out.println("  Ausdruck:  " + aufgabe.render());
        if (!aufgabe.belegung().isBlank()) {
            System.out.println("  Gegeben:   " + aufgabe.belegung());
        }
        System.out.println("  Korrekt:   " + referenz.ergebnisText());
        System.out.println("  Eingabe:   " + beschreibe(antwort));

        if (diagnose.korrekt()) {
            System.out.println("  Diagnose:  keine, Antwort ist richtig");
        } else {
            System.out.println("  Diagnose:  " + kategorienText(diagnose)
                    + "  [" + diagnose.konfidenz()
                    + (diagnose.erklaert() ? "" : ", nur vermutet") + "]");
            diagnose.subtype().ifPresent(s -> System.out.println("  Untertyp:  " + s.id() + " " + s.name()));
            System.out.println("  Grund:     " + diagnose.begruendung());
        }
        System.out.println();
    }

    /** Bei einer Kombination alle beteiligten Kategorien, sonst die eine. */
    private static String kategorienText(Diagnose diagnose) {
        if (diagnose.kategorien().isEmpty()) {
            return "unbekannt";
        }
        StringBuilder sb = new StringBuilder();
        for (Misconception m : diagnose.kategorien()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(m.id()).append(" ").append(m.name());
        }
        return sb.toString();
    }

    private static String beschreibe(NutzerAntwort antwort) {
        return antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar";
    }
}
