package de.lmu.tutor.gen;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static de.lmu.tutor.ast.AST.*;

/**
 * Erzeugt zu einer Bug-Kategorie (B01-B14) einen zufaelligen, typkonformen
 * Ausdruck mit gueltiger Variablenbelegung. Die erzeugten Aufgaben sind so
 * gebaut, dass sie der {@code StepEvaluator} eindeutig loesen kann -- ausser
 * Kategorie B08, die absichtlich nicht auswertbar ist.
 *
 * <p>Mit einem festen Seed ({@link #AufgabenGenerator(long)}) sind die Aufgaben
 * reproduzierbar -- praktisch fuer Tests.</p>
 */
public final class AufgabenGenerator {

    private static final String[] WOERTER = {"rot", "gelb", "blau", "gruen", "Java", "Test", "Hallo", "Welt"};

    private final Random random;

    public AufgabenGenerator() { this(new Random()); }
    public AufgabenGenerator(long seed) { this(new Random(seed)); }
    public AufgabenGenerator(Random random) { this.random = random; }

    /** Erzeugt eine Aufgabe der untersten Schwierigkeitsstufe. */
    public Aufgabe generiere(String kategorieId) {
        return generiere(kategorieId, 1);
    }

    /**
     * Erzeugt eine Aufgabe fuer die gegebene Kategorie in der gewuenschten
     * Schwierigkeitsstufe.
     *
     * <p>Die Stufen unterscheiden sich in der Zahl der Schritte, nicht in der
     * Fehlvorstellung. Jede Stufe prueft dieselbe Kategorie:</p>
     *
     * <ul>
     *   <li><b>Stufe 1</b> stellt die Fehlvorstellung allein. Ein Operator, zwei
     *       Operanden, keine Variablen ausser den noetigen.</li>
     *   <li><b>Stufe 2</b> setzt einen Schritt davor oder dahinter, meist mit Variablen
     *       aus der Belegung. Wer den Kern nicht beherrscht, scheitert weiterhin, wer ihn
     *       beherrscht, muss zusaetzlich die Reihenfolge einhalten.</li>
     *   <li><b>Stufe 3</b> entspricht dem Zuschnitt der Altklausuren: mehrere Schritte,
     *       Indexzugriffe und Ketten, und damit auch Kombinationen mehrerer
     *       Fehlvorstellungen in einem Ausdruck.</li>
     * </ul>
     *
     * <p>Zu jeder Kategorie muss der Fehlersimulator auch in den hoeheren Stufen einen
     * Wert erzeugen koennen, der sich vom richtigen unterscheidet. Sonst waere die Aufgabe
     * zwar schwerer, aber nicht mehr diagnostisch. {@code AufgabenGeneratorStufenTest}
     * prueft das fuer jede Kategorie und jede Stufe.</p>
     *
     * @param stufe 1 bis 3; hoehere Werte werden auf 3 begrenzt
     */
    public Aufgabe generiere(String kategorieId, int stufe) {
        int s = hoechsteGepruefteStufe(kategorieId, Math.max(1, Math.min(3, stufe)));
        return nurBenutzteVariablen(switch (kategorieId) {
            case "B01" -> switch (s) { case 3 -> b01s3(); case 2 -> b01s2(); default -> b01(); };
            case "B02" -> b02();
            case "B03" -> s == 3 ? b03s3() : b03();
            case "B04" -> b04();
            case "B05" -> b05();
            case "B06" -> switch (s) { case 3 -> b06s3(); case 2 -> b06s2(); default -> b06(); };
            case "B07" -> s >= 2 ? b07s2() : b07();
            case "B08" -> switch (s) { case 3 -> b08s3(); case 2 -> b08s2(); default -> b08(); };
            case "B09" -> switch (s) { case 3 -> b09s3(); case 2 -> b09s2(); default -> b09(); };
            case "B10" -> s == 3 ? b10s3() : b10();
            case "B11" -> switch (s) { case 3 -> b11s3(); case 2 -> b11s2(); default -> b11(); };
            case "B12" -> b12();
            case "B13" -> switch (s) { case 3 -> b13s3(); case 2 -> b13s2(); default -> b13(); };
            case "B14" -> switch (s) { case 3 -> b14s3(); case 2 -> b14s2(); default -> b14(); };
            default -> throw new IllegalArgumentException("Keine Vorlage fuer Kategorie: " + kategorieId);
        });
    }

    /** Reihenfolge, in der Variablen in der Belegung stehen. Alles Uebrige haengt hinten an. */
    private static final List<String> ANZEIGEREIHENFOLGE = List.of("a", "s", "k", "i", "x");

    /**
     * Baut die angezeigte Belegung aus den Variablen neu auf, die im Ausdruck tatsaechlich
     * vorkommen.
     *
     * <p>Die Vorlagen legen k und i gemeinsam an, weil sich mehrere Stufen derselben
     * Kategorie denselben Kontext teilen. Angezeigt wurde bisher beides, auch wenn im
     * Ausdruck nur k stand. In einer Diagnoseaufgabe ist eine solche Zugabe nicht harmlos:
     * Wer i genannt bekommt und im Ausdruck nicht findet, sucht zuerst den eigenen Lesefehler
     * statt zu rechnen. Die Zeit dafuer steht hinterher im Protokoll als Bearbeitungsdauer
     * dieser Aufgabe, obwohl sie nichts mit der Fehlvorstellung zu tun hat.</p>
     *
     * <p>Der Kontext selbst bleibt unveraendert und vollstaendig. Er wird nicht angezeigt,
     * kostet nichts, und {@code ++k} schreibt waehrend der Auswertung dorthin zurueck.</p>
     */
    private Aufgabe nurBenutzteVariablen(Aufgabe aufgabe) {
        Set<String> benutzt = new LinkedHashSet<>();
        sammleVariablen(aufgabe.ausdruck(), benutzt);
        List<String> namen = new ArrayList<>(benutzt);
        namen.sort(Comparator.comparingInt(name -> {
            int platz = ANZEIGEREIHENFOLGE.indexOf(name);
            return platz < 0 ? ANZEIGEREIHENFOLGE.size() : platz;
        }));

        StringBuilder sb = new StringBuilder();
        for (String name : namen) {
            Value wert = aufgabe.kontext().hole(name);
            if (wert == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name).append(" = ").append(wert.render());
        }
        return new Aufgabe(aufgabe.kategorieId(), aufgabe.ausdruck(), aufgabe.kontext(),
                sb.toString());
    }

    /** Sammelt die Namen aller Variablen im Teilbaum, auch die in {@code ++k} und {@code a[k]}. */
    private static void sammleVariablen(Expr ausdruck, Set<String> ziel) {
        if (ausdruck instanceof Expr.Var v) {
            ziel.add(v.name());
        }
        for (Expr kind : ausdruck.children()) {
            sammleVariablen(kind, ziel);
        }
    }

    /**
     * Begrenzt die Stufe auf das, was fuer diese Kategorie nachweislich diagnostizierbar
     * bleibt.
     *
     * <p>Eine schwerere Aufgabe nuetzt nichts, wenn das System die Fehlvorstellung darin
     * nicht mehr erkennt. Die Regeln des Fehlersimulators setzen an der Wurzel des
     * Ausdrucks an: {@code 22 / 6} wird erkannt, {@code 22 / 6 + 8} nicht mehr, weil dort
     * oben ein Plus steht. Nur wo eine Regel den Baum durchlaeuft, wie bei der
     * Praezedenzkette und beim Method-Chaining, oder wo gar keine Simulation noetig ist,
     * ueberlebt die Diagnose eine Einbettung.</p>
     *
     * <p>Welche Kombination aus Kategorie und Stufe das erfuellt, ist nicht geschaetzt,
     * sondern gemessen: {@code AufgabenStufenTest} erzeugt je Kombination Aufgaben und
     * prueft, dass die Fehlvorstellung weiterhin zu einem anderen Ergebnis fuehrt als die
     * richtige Loesung. Faellt eine Kombination durch, gehoert sie hier heraus.</p>
     *
     * <p>Nicht eingebettet werden deshalb B02, B04, B05 und B12 sowie B03 und B10 in
     * Stufe 2 und B07 in Stufe 3. Diese Kategorien bleiben auf Stufe 1.</p>
     */
    private static int hoechsteGepruefteStufe(String kategorieId, int gewuenscht) {
        int grenze = switch (kategorieId) {
            case "B02", "B04", "B05", "B12" -> 1;
            case "B07" -> 2;
            default -> 3;
        };
        // B03 und B10 haben keine brauchbare Stufe 2, wohl aber eine Stufe 3.
        if (gewuenscht == 2 && (kategorieId.equals("B03") || kategorieId.equals("B10"))) {
            return 1;
        }
        return Math.min(gewuenscht, grenze);
    }

    // ---- Vorlagen je Kategorie ----

    /**
     * Operatorpraezedenz. Drei Formen wechseln sich ab:
     * {@code x + y * z}, {@code x + y / z} und {@code x + y % z}.
     *
     * <p>Die beiden letzten Formen sind noetig, damit <em>Fehlerkombinationen</em>
     * ueberhaupt auftreten koennen. Wer die Praezedenz missachtet, wendet beim Rechnen von
     * links nach rechts zugleich seine eigene Vorstellung von Division oder Modulo an. Bei
     * {@code 12 + 9 / 2} etwa ergibt die korrekte Auswertung 16, links nach rechts
     * gerechnet 10, und mit zusaetzlich angenommener Fliesskommadivision 10.5. Nur der
     * letzte Wert laesst sich allein durch zwei gleichzeitige Fehlvorstellungen erklaeren.</p>
     *
     * <p>Mit der reinen Form {@code x + y * z} gaebe es keinen solchen Fall, denn ohne
     * Division oder Modulo wirkt sich nur die Reihenfolge aus.</p>
     */
    private Aufgabe b01() {
        return switch (random.nextInt(3)) {
            case 1 -> b01MitDivision();
            case 2 -> b01MitModulo();
            default -> ohneVars("B01",
                    bin("+", intLit(rnd(2, 9)), bin("*", intLit(rnd(2, 9)), intLit(rnd(2, 9)))));
        };
    }

    /**
     * {@code x + y / z}, so gewaehlt, dass sich drei Ergebnisse unterscheiden: die korrekte
     * Loesung, die Links-nach-rechts-Rechnung und deren Fliesskomma-Variante. Nur dann ist
     * die Kombination diagnostisch brauchbar.
     */
    private Aufgabe b01MitDivision() {
        for (int versuch = 0; versuch < 50; versuch++) {
            int z = rnd(2, 6);
            int y = rnd(4, 30);
            int x = rnd(2, 12);
            int korrekt = x + y / z;
            int linksNachRechts = (x + y) / z;
            boolean restBleibt = (x + y) % z != 0;
            if (korrekt != linksNachRechts && restBleibt) {
                return ohneVars("B01", bin("+", intLit(x), bin("/", intLit(y), intLit(z))));
            }
        }
        return ohneVars("B01", bin("+", intLit(5), bin("/", intLit(9), intLit(2))));
    }

    /**
     * {@code x + y % z}, so gewaehlt, dass korrekte Loesung, Links-nach-rechts-Rechnung und
     * die Variante mit Modulo als Division jeweils verschieden ausfallen.
     */
    private Aufgabe b01MitModulo() {
        for (int versuch = 0; versuch < 50; versuch++) {
            int z = rnd(3, 7);
            int y = rnd(10, 30);
            int x = rnd(2, 12);
            int korrekt = x + y % z;
            int linksNachRechts = (x + y) % z;
            int moduloAlsDivision = (x + y) / z;
            if (korrekt != linksNachRechts && linksNachRechts != moduloAlsDivision) {
                return ohneVars("B01", bin("+", intLit(x), bin("%", intLit(y), intLit(z))));
            }
        }
        return ohneVars("B01", bin("+", intLit(7), bin("%", intLit(20), intLit(6))));
    }

    private Aufgabe b02() { // Ganzzahldivision: x / y (nicht glatt teilbar)
        int y = rnd(2, 9), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B02", bin("/", intLit(x), intLit(y)));
    }

    private Aufgabe b03() { // String-Konkatenation: "NN" + n
        return ohneVars("B03", bin("+", stringLit(String.valueOf(rnd(10, 99))), intLit(rnd(1, 9))));
    }

    /**
     * Cast: {@code (int) d} mit Nachkommastellen.
     *
     * <p>Die Nachkommastelle liegt bewusst bei mindestens fuenf. Die Fehlvorstellung
     * lautet, der Cast runde, statt abzuschneiden. Bei {@code (int) 2.1} ergeben Runden
     * und Abschneiden beide 2, die Fehlvorstellung waere also nicht zu beobachten und die
     * Aufgabe damit fuer diese Kategorie wertlos. Bei {@code (int) 2.7} unterscheiden sich
     * die beiden Wege.</p>
     */
    private Aufgabe b04() {
        double d = rnd(1, 9) + rnd(5, 9) / 10.0;
        return ohneVars("B04", cast(JType.INT, doubleLit(d)));
    }

    /**
     * Array-Index: {@code a[k + 1]}, wobei {@code k + 1} immer gueltig ist.
     *
     * <p>Die Eintraege sind verschieden, siehe {@link #zufallsArray(int)}. Stuenden an
     * {@code a[k]} und {@code a[k + 1]} dieselben Woerter, lieferte der ignorierte Versatz
     * dasselbe Ergebnis wie der richtige Weg, und die Fehlvorstellung waere nicht zu
     * beobachten.</p>
     */
    private Aufgabe b05() {
        String[] arr = zufallsArray(rnd(4, 5));
        int k = rnd(0, arr.length - 2);
        EvaluationContext ctx = new EvaluationContext().setzeStringArray("a", arr).setzeInt("k", k);
        return new Aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx, belegungArray(arr, "k", k));
    }

    /**
     * Method-Chaining: eine Kette aus genau zwei Aufrufen.
     *
     * <p>Fuenf Bauformen, damit nicht immer dasselbe dasteht und das Ergebnis nicht immer
     * denselben Datentyp hat. Herauskommen String, char und int.</p>
     *
     * <p><b>Warum die Gross- und Kleinschreibung des Wortes zur Bauform passen muss.</b>
     * Eine Kette taugt nur dann als Aufgabe, wenn sich <em>beide</em> Glieder auf das
     * Ergebnis auswirken. Bei {@code "gelb".substring(1).toLowerCase()} aendert
     * {@code toLowerCase} nichts mehr, das Auslassen dieses Glieds waere also nicht zu
     * beobachten und die Fehlvorstellung nicht diagnostizierbar. Deshalb bekommen die
     * Bauformen mit {@code toLowerCase} ein durchgehend grosses Wort und die mit
     * {@code toUpperCase} ein kleines.</p>
     */
    private Aufgabe b06() {
        Expr e = switch (random.nextInt(5)) {
            // "JAVA".substring(1).toLowerCase()  ->  "ava" : String
            case 0 -> call(call(stringLit(grossWort(3)), "substring", intLit(1)), "toLowerCase");
            // "java".toUpperCase().substring(2)  ->  "VA" : String
            case 1 -> call(call(stringLit(kleinWort(3)), "toUpperCase"), "substring", intLit(2));
            // "java".substring(1, 3).toUpperCase()  ->  "AV" : String
            case 2 -> call(call(stringLit(kleinWort(3)), "substring", intLit(1), intLit(3)),
                    "toUpperCase");
            // "JAVA".toLowerCase().charAt(1)  ->  'a' : char
            case 3 -> call(call(stringLit(grossWort(3)), "toLowerCase"), "charAt", intLit(1));
            // "JAVA".substring(1).length()  ->  3 : int
            default -> call(call(stringLit(grossWort(3)), "substring", intLit(1)), "length");
        };
        return ohneVars("B06", e);
    }

    private Aufgabe b07() { // Datentyp: x / y (int-Division)
        int y = rnd(2, 9), x;
        do { x = rnd(3, 20); } while (x % y == 0);
        return ohneVars("B07", bin("/", intLit(x), intLit(y)));
    }

    private Aufgabe b08() { // nicht auswertbar: n + true  ODER  ("NN" + n) - m
        Expr e = random.nextBoolean()
                ? bin("+", intLit(rnd(1, 9)), boolLit(true))
                : bin("-", bin("+", stringLit(String.valueOf(rnd(10, 99))), intLit(rnd(1, 9))), intLit(rnd(1, 9)));
        return ohneVars("B08", e);
    }

    /**
     * Kurzschlussauswertung: Der linke Operand entscheidet, der rechte wuerde abstuerzen.
     *
     * <p><b>Warum der linke Operand nicht immer eine Konstante ist.</b> Bei
     * {@code false && ...} steht die Antwort nach dem ersten Blick fest, und wer die
     * Aufgabe einmal gesehen hat, traegt beim naechsten Mal ohne Nachdenken wieder
     * {@code false} ein. Dann misst die Aufgabe nicht mehr das Verstaendnis der
     * Kurzschlussauswertung, sondern nur noch die Erinnerung. Deshalb steht links in der
     * Haelfte der Faelle ein Vergleich, der erst ausgerechnet werden muss.</p>
     *
     * <p>Mit {@code ||} ist das Ergebnis {@code true}, mit {@code &&} ist es
     * {@code false}. Auch das wechselt also, waehrend vorher jede Aufgabe dieser Kategorie
     * auf {@code false} hinauslief.</p>
     *
     * <p>Der rechte Operand ist immer ein Ausdruck, der fuer sich genommen nicht
     * auswertbar waere, also eine Division durch null, ein Modulo mit null oder ein
     * Zugriff hinter das Ende einer Zeichenkette. Genau daran zeigt sich die
     * Fehlvorstellung: Wer glaubt, Java werte beide Seiten aus, traegt "nicht auswertbar"
     * ein.</p>
     */
    private Aufgabe b09() {
        boolean oder = random.nextBoolean();
        Expr links = random.nextBoolean()
                ? boolLit(oder)
                : vergleichMitWert(oder);
        return ohneVars("B09", bin(oder ? "||" : "&&", links, riskanterAusdruck()));
    }

    /** Ein Vergleich zweier Zahlen, dessen Ergebnis feststeht. */
    private Expr vergleichMitWert(boolean sollWahrSein) {
        int a = rnd(2, 9);
        int b = rnd(10, 30);
        return sollWahrSein
                ? bin("<", intLit(a), intLit(b))
                : bin(">", intLit(a), intLit(b));
    }

    /** Ein Ausdruck, der fuer sich genommen einen Laufzeitfehler ausloesen wuerde. */
    private Expr riskanterAusdruck() {
        return switch (random.nextInt(3)) {
            case 0 -> bin("==", bin("/", intLit(rnd(1, 9)), intLit(0)), intLit(0));
            case 1 -> bin("==", bin("%", intLit(rnd(10, 40)), intLit(0)), intLit(0));
            default -> bin("==", call(stringLit(wort()), "charAt", intLit(rnd(12, 20))),
                    charLit('a'));
        };
    }

    /**
     * Modulo: {@code x % y} mit echtem Rest.
     *
     * <p>Zusaetzlich muss sich der Rest vom Quotienten unterscheiden. Die Fehlvorstellung
     * lautet, {@code %} liefere den Quotienten. Bei {@code 36 % 8} sind Rest und Quotient
     * beide 4, die Fehlvorstellung waere also nicht zu beobachten.</p>
     */
    private Aufgabe b10() {
        int y = rnd(2, 9), x;
        do { x = rnd(10, 40); } while (x % y == 0 || x % y == x / y);
        return ohneVars("B10", bin("%", intLit(x), intLit(y)));
    }

    /**
     * Ternaerer Operator: {@code (x > 0) ? a : b}.
     *
     * <p>Die beiden Zweige tragen verschiedene Werte. Waeren sie gleich, lieferte der
     * falsche Zweig dasselbe Ergebnis wie der richtige, und die Fehlvorstellung waere
     * nicht zu beobachten.</p>
     */
    private Aufgabe b11() {
        int x = rnd(-5, 5);
        int dann = rnd(1, 9), sonst;
        do { sonst = rnd(1, 9); } while (sonst == dann);
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", x);
        Expr e = ternary(bin(">", var("x"), intLit(0)), intLit(dann), intLit(sonst));
        return new Aufgabe("B11", e, ctx, "x = " + x);
    }

    private Aufgabe b12() { // Inkrement: ++x oder x++
        int x = rnd(1, 9);
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", x);
        Expr e = random.nextBoolean() ? preInc(var("x")) : postInc(var("x"));
        return new Aufgabe("B12", e, ctx, "x = " + x);
    }

    private Aufgabe b13() { // charAt: "WORT".charAt(i)
        String w = wortMitLaenge(2);
        return ohneVars("B13", call(stringLit(w), "charAt", intLit(rnd(0, w.length() - 1))));
    }

    /**
     * String-Vergleich: {@code s.equals("wort")}.
     *
     * <p><b>Nur der Gleichheitsfall ist diagnostisch.</b> Die Fehlvorstellung lautet,
     * {@code equals} vergleiche Referenzen und liefere deshalb {@code false}. Ist die
     * richtige Antwort ohnehin {@code false}, fuehrt die Fehlvorstellung zum selben Wert
     * und bleibt unsichtbar. Das liegt an der Fehlvorstellung selbst und nicht am
     * Generator.</p>
     *
     * <p>Trotzdem kommt der ungleiche Fall vor, sonst waere die Antwort hier immer
     * {@code true} und nach der zweiten Aufgabe zu erraten. Im ungleichen Fall
     * unterscheiden sich die Woerter nur in der Gross- und Kleinschreibung, denn dann
     * muss man wenigstens genau hinsehen.</p>
     */
    private Aufgabe b14() {
        String w = wort();
        String vergleich = random.nextBoolean() ? w : andereSchreibweise(w);
        EvaluationContext ctx = new EvaluationContext().setzeString("s", w);
        return new Aufgabe("B14", call(var("s"), "equals", stringLit(vergleich)), ctx, "s = \"" + w + "\"");
    }

    // =====================================================================
    // Stufe 2 und 3
    //
    // Gemeinsame Regel: Der Kern der Fehlvorstellung bleibt unveraendert, es kommen nur
    // Schritte davor oder dahinter. Wird der Kern gestoert, veraendert sich dadurch auch
    // das Gesamtergebnis, und die Kategorie bleibt diagnostizierbar.
    //
    // Bewusst NICHT verwendet werden mehrere Seiteneffekte in einem Ausdruck, etwa
    // b == b++ aus der Altklausur. Solche Ausdruecke sind zwar gueltiges Java, ihr Wert
    // haengt aber an der strikten Auswertungsreihenfolge. Zwei Tage vor der Erhebung ist
    // das ein Risiko ohne Gegenwert.
    // =====================================================================

    /** Belegung zweier ganzzahliger Variablen, in der Schreibweise der Altklausuren. */
    private EvaluationContext ctxKI(int k, int i) {
        return new EvaluationContext().setzeInt("k", k).setzeInt("i", i);
    }

    private String belegungKI(int k, int i) {
        return "k = " + k + ", i = " + i;
    }

    // ---- B01 Praezedenz ----

    /** {@code x - y * z + w}: zwei Operatoren gleicher und einer hoeherer Stufe. */
    private Aufgabe b01s2() {
        return ohneVars("B01", bin("+",
                bin("-", intLit(rnd(20, 40)), bin("*", intLit(rnd(2, 6)), intLit(rnd(2, 6)))),
                intLit(rnd(2, 9))));
    }

    /** {@code k % m + i + x / y}, der Zuschnitt aus der Altklausur. */
    private Aufgabe b01s3() {
        int k = rnd(5, 19), i = rnd(2, 9), m = rnd(3, 7), x = rnd(10, 40), y = rnd(3, 7);
        Expr e = bin("+", bin("+", bin("%", var("k"), intLit(m)), var("i")),
                bin("/", intLit(x), intLit(y)));
        return new Aufgabe("B01", e, ctxKI(k, i), belegungKI(k, i));
    }

    // ---- B02 Integer-Division ----

    /** {@code x / y + z}. */
    private Aufgabe b02s2() {
        int y = rnd(3, 8), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B02", bin("+", bin("/", intLit(x), intLit(y)), intLit(rnd(2, 9))));
    }

    /** {@code x / y * y}: zeigt das Abschneiden besonders deutlich, denn heraus kommt nicht x. */
    private Aufgabe b02s3() {
        int y = rnd(3, 8), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B02", bin("*", bin("/", intLit(x), intLit(y)), intLit(y)));
    }

    // ---- B03 Konkatenation ----

    /** {@code "w" + k}, mit einer Variablen aus der Belegung. */
    private Aufgabe b03s2() {
        int k = rnd(2, 9), i = rnd(2, 9);
        Expr e = bin("+", stringLit(wort()), var("k"));
        return new Aufgabe("B03", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** {@code k + "" + 2 * i}: erst Zahl plus Text, dann Punktrechnung angehaengt. */
    private Aufgabe b03s3() {
        int k = rnd(2, 9), i = rnd(2, 9);
        Expr e = bin("+", bin("+", var("k"), stringLit("")),
                bin("*", intLit(2), var("i")));
        return new Aufgabe("B03", e, ctxKI(k, i), belegungKI(k, i));
    }

    // ---- B04 Cast ----

    /** {@code (int)(k / 8.0) + z}, der Zuschnitt aus der Altklausur. */
    private Aufgabe b04s2() {
        int k = rnd(10, 60), i = rnd(2, 9);
        Expr e = bin("+", cast(JType.INT, bin("/", var("k"), doubleLit(8.0))), intLit(rnd(2, 9)));
        return new Aufgabe("B04", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** {@code (int)(x * 1.5) - y}. */
    private Aufgabe b04s3() {
        int x;
        do { x = rnd(3, 19); } while (x % 2 == 0);
        return ohneVars("B04", bin("-",
                cast(JType.INT, bin("*", intLit(x), doubleLit(1.5))), intLit(rnd(2, 9))));
    }

    // ---- B05 Array-Index ----

    /** {@code a[k + 1].length() * k}, der Zuschnitt aus der Altklausur. */
    private Aufgabe b05s2() {
        String[] arr = zufallsArrayVerschiedenLang(4);
        int k = rnd(0, arr.length - 2), i = rnd(2, 9);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = bin("*", call(index(var("a"), bin("+", var("k"), intLit(1))), "length"), var("k"));
        return new Aufgabe("B05", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    /** {@code a[k].length() + a[i].length()}: zwei Zugriffe, beide muessen stimmen. */
    private Aufgabe b05s3() {
        String[] arr = zufallsArrayVerschiedenLang(4);
        int k = rnd(0, arr.length - 1), i;
        do { i = rnd(0, arr.length - 1); } while (i == k);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = bin("+", call(index(var("a"), var("k")), "length"),
                call(index(var("a"), var("i")), "length"));
        return new Aufgabe("B05", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    // ---- B06 Method-Chaining ----

    /** {@code a[k].substring(1).toLowerCase()}, der Zuschnitt aus der Altklausur. */
    private Aufgabe b06s2() {
        String[] arr = grossArray(4);
        int k = rnd(0, arr.length - 1), i = rnd(2, 9);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(call(index(var("a"), var("k")), "substring", intLit(1)), "toLowerCase");
        return new Aufgabe("B06", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    /** {@code a[k].toLowerCase().charAt(1)}: drei Schritte, Ergebnis ist ein char. */
    private Aufgabe b06s3() {
        String[] arr = grossArray(4);
        int k = rnd(0, arr.length - 1), i = rnd(2, 9);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(call(index(var("a"), var("k")), "toLowerCase"), "charAt", intLit(1));
        return new Aufgabe("B06", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    // ---- B07 Datentyp ----

    /** {@code x / y + z}: der Wert ueberrascht, der Typ bleibt int. */
    private Aufgabe b07s2() {
        int y = rnd(3, 8), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B07", bin("+", bin("/", intLit(x), intLit(y)), intLit(rnd(2, 9))));
    }

    /** {@code x / y + 0.5}: sobald eine Kommazahl beteiligt ist, wird das Ganze double. */
    private Aufgabe b07s3() {
        int y = rnd(3, 8), x;
        do { x = rnd(10, 40); } while (x % y == 0);
        return ohneVars("B07", bin("+", bin("/", intLit(x), intLit(y)), doubleLit(0.5)));
    }

    // ---- B08 nicht auswertbar ----

    /** {@code "nn" + x - k}, der Zuschnitt aus der Altklausur. */
    private Aufgabe b08s2() {
        int k = rnd(2, 9), i = rnd(2, 9);
        Expr e = bin("-", bin("+", stringLit(String.valueOf(rnd(10, 99))), intLit(rnd(1, 9))),
                var("k"));
        return new Aufgabe("B08", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** {@code a[k] * i}: ein Text laesst sich nicht multiplizieren. */
    private Aufgabe b08s3() {
        String[] arr = zufallsArray(4);
        int k = rnd(0, arr.length - 1), i = rnd(2, 9);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = bin("*", index(var("a"), var("k")), var("i"));
        return new Aufgabe("B08", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    // ---- B09 Kurzschluss ----

    /** Links ein Vergleich mit Variablen, rechts ein Ausdruck, der abstuerzen wuerde. */
    private Aufgabe b09s2() {
        boolean oder = random.nextBoolean();
        int k = rnd(2, 9), i = rnd(10, 30);
        Expr links = oder ? bin("<", var("k"), var("i")) : bin(">", var("k"), var("i"));
        Expr e = bin(oder ? "||" : "&&", links, riskanterAusdruck());
        return new Aufgabe("B09", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** Rechts ein Indexzugriff hinter das Ende des Feldes. */
    private Aufgabe b09s3() {
        boolean oder = random.nextBoolean();
        String[] arr = zufallsArray(3);
        int k = rnd(2, 9), i = rnd(10, 30);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr links = oder ? bin("<", var("k"), var("i")) : bin(">", var("k"), var("i"));
        Expr rechts = bin(">", call(index(var("a"), var("i")), "length"), intLit(0));
        Expr e = bin(oder ? "||" : "&&", links, rechts);
        return new Aufgabe("B09", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    // ---- B10 Modulo ----

    /** {@code x % y + z}. */
    private Aufgabe b10s2() {
        int y = rnd(3, 9), x;
        do { x = rnd(10, 40); } while (x % y == 0 || x % y == x / y);
        return ohneVars("B10", bin("+", bin("%", intLit(x), intLit(y)), intLit(rnd(2, 9))));
    }

    /** {@code (x + k) % y}: der Rest wird erst nach einer Rechnung gebildet. */
    private Aufgabe b10s3() {
        int k = rnd(2, 9), i = rnd(2, 9);
        int y = rnd(3, 9), x;
        do { x = rnd(10, 40); } while ((x + k) % y == 0 || (x + k) % y == (x + k) / y);
        Expr e = bin("%", bin("+", intLit(x), var("k")), intLit(y));
        return new Aufgabe("B10", e, ctxKI(k, i), belegungKI(k, i));
    }

    // ---- B11 Ternaerer Operator ----

    /** {@code (k > i) ? k * 2 : i * 2}: beide Zweige rechnen. */
    private Aufgabe b11s2() {
        int k = rnd(2, 9), i;
        do { i = rnd(2, 9); } while (i == k);
        Expr e = ternary(bin(">", var("k"), var("i")),
                bin("*", var("k"), intLit(2)), bin("*", var("i"), intLit(2)));
        return new Aufgabe("B11", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** {@code (k + i > m) ? k % 3 : i / 2}: die Bedingung muss selbst ausgerechnet werden. */
    private Aufgabe b11s3() {
        int k, i, m;
        // Die beiden Zweige muessen verschiedene Werte liefern, sonst faellt der falsche
        // Zweig nicht auf. Dieselbe Bedingung wie in Stufe 1, hier nur ueber die
        // berechneten Zweige statt ueber zwei Konstanten.
        do {
            k = rnd(2, 9);
            i = rnd(2, 9);
            m = rnd(4, 14);
        } while (k % 3 == i / 2);
        Expr e = ternary(bin(">", bin("+", var("k"), var("i")), intLit(m)),
                bin("%", var("k"), intLit(3)), bin("/", var("i"), intLit(2)));
        return new Aufgabe("B11", e, ctxKI(k, i), belegungKI(k, i));
    }

    // ---- B12 Inkrement ----

    /** {@code k++ + z} oder {@code ++k + z}: ein einziger Seiteneffekt, dahinter eine Rechnung. */
    private Aufgabe b12s2() {
        int k = rnd(2, 9), i = rnd(2, 9);
        Expr kern = random.nextBoolean() ? preInc(var("k")) : postInc(var("k"));
        Expr e = bin("+", kern, intLit(rnd(2, 9)));
        return new Aufgabe("B12", e, ctxKI(k, i), belegungKI(k, i));
    }

    /** {@code (++k) * z - i} beziehungsweise mit Postfix. */
    private Aufgabe b12s3() {
        int k = rnd(2, 9), i = rnd(2, 9);
        Expr kern = random.nextBoolean() ? preInc(var("k")) : postInc(var("k"));
        Expr e = bin("-", bin("*", kern, intLit(rnd(2, 5))), var("i"));
        return new Aufgabe("B12", e, ctxKI(k, i), belegungKI(k, i));
    }

    // ---- B13 char ----

    /** {@code a[k].charAt(i)}: der Zugriff kommt vor dem Zeichen. */
    private Aufgabe b13s2() {
        String[] arr = zufallsArrayMitMindestlaenge(4, 4);
        int k = rnd(0, arr.length - 1);
        int i = rnd(0, 3);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(index(var("a"), var("k")), "charAt", var("i"));
        return new Aufgabe("B13", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    /** {@code a[k].substring(1).charAt(0)}: zwei Schritte bis zum Zeichen. */
    private Aufgabe b13s3() {
        String[] arr = zufallsArrayMitMindestlaenge(4, 3);
        int k = rnd(0, arr.length - 1), i = rnd(2, 9);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(call(index(var("a"), var("k")), "substring", intLit(1)), "charAt", intLit(0));
        return new Aufgabe("B13", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    // ---- B14 equals ----

    /** {@code a[k].equals(a[i])}: zwei Zugriffe, dann der Vergleich. */
    private Aufgabe b14s2() {
        String[] arr = zufallsArray(4);
        int k = rnd(0, arr.length - 1);
        // In der Haelfte der Faelle dieselbe Stelle, dann ist das Ergebnis true und die
        // Fehlvorstellung sichtbar. Sonst eine andere Stelle, sonst waere es zu erraten.
        int i = random.nextBoolean() ? k : andererIndex(arr.length, k);
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(index(var("a"), var("k")), "equals", index(var("a"), var("i")));
        return new Aufgabe("B14", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    /** {@code a[k].toLowerCase().equals("wort")}: erst umwandeln, dann vergleichen. */
    private Aufgabe b14s3() {
        String[] arr = grossArray(4);
        int k = rnd(0, arr.length - 1), i = rnd(2, 9);
        String vergleich = random.nextBoolean()
                ? arr[k].toLowerCase()
                : andereSchreibweise(arr[k].toLowerCase());
        EvaluationContext ctx = ctxKI(k, i).setzeStringArray("a", arr);
        Expr e = call(call(index(var("a"), var("k")), "toLowerCase"), "equals",
                stringLit(vergleich));
        return new Aufgabe("B14", e, ctx, belegungArray(arr, "k", k) + ", i = " + i);
    }

    /** Ein anderer Index als der gegebene. */
    private int andererIndex(int laenge, int ausser) {
        int j;
        do { j = random.nextInt(laenge); } while (j == ausser);
        return j;
    }

    /** Lauter grossgeschriebene Woerter, damit {@code toLowerCase} sichtbar wirkt. */
    private String[] grossArray(int n) {
        String[] arr = zufallsArrayMitMindestlaenge(n, 3);
        for (int j = 0; j < arr.length; j++) {
            arr[j] = arr[j].toUpperCase();
        }
        return arr;
    }

    /** Verschiedene Woerter, alle mindestens so lang wie verlangt. */
    private String[] zufallsArrayMitMindestlaenge(int n, int minLaenge) {
        List<String> vorrat = new ArrayList<>();
        for (String w : WOERTER) {
            if (w.length() >= minLaenge) {
                vorrat.add(w);
            }
        }
        Collections.shuffle(vorrat, random);
        return vorrat.subList(0, Math.min(n, vorrat.size())).toArray(new String[0]);
    }

    /**
     * Verschiedene Woerter mit paarweise verschiedener Laenge.
     *
     * <p>Noetig, wo {@code length()} auf einen Eintrag angewendet wird: Haetten zwei
     * Eintraege dieselbe Laenge, lieferte ein falsch berechneter Index dasselbe Ergebnis
     * wie der richtige, und der Indexfehler bliebe unsichtbar.</p>
     */
    private String[] zufallsArrayVerschiedenLang(int n) {
        List<String> vorrat = new ArrayList<>(List.of(WOERTER));
        Collections.shuffle(vorrat, random);
        List<String> gewaehlt = new ArrayList<>();
        List<Integer> laengen = new ArrayList<>();
        for (String w : vorrat) {
            if (!laengen.contains(w.length())) {
                gewaehlt.add(w);
                laengen.add(w.length());
            }
            if (gewaehlt.size() == n) {
                break;
            }
        }
        return gewaehlt.toArray(new String[0]);
    }

    // ---- Hilfsmittel ----

    private int rnd(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** Dasselbe Wort in anderer Gross- und Kleinschreibung, also ungleich fuer equals. */
    private String andereSchreibweise(String w) {
        String anders = w.equals(w.toLowerCase()) ? w.toUpperCase() : w.toLowerCase();
        // Sollte ein Wort beides zugleich sein, bleibt ein anderes Wort als Ausweg.
        if (anders.equals(w)) {
            String ersatz;
            do { ersatz = WOERTER[random.nextInt(WOERTER.length)]; } while (ersatz.equals(w));
            return ersatz;
        }
        return anders;
    }

    private String wort() {
        return WOERTER[random.nextInt(WOERTER.length)];
    }

    /** Ein durchgehend grosses Wort, damit {@code toLowerCase} sichtbar wirkt. */
    private String grossWort(int minLaenge) {
        return wortMitLaenge(minLaenge).toUpperCase();
    }

    /** Ein durchgehend kleines Wort, damit {@code toUpperCase} sichtbar wirkt. */
    private String kleinWort(int minLaenge) {
        return wortMitLaenge(minLaenge).toLowerCase();
    }

    private String wortMitLaenge(int minLaenge) {
        String w;
        do { w = wort(); } while (w.length() < minLaenge);
        return w;
    }

    /**
     * Ein Feld mit lauter verschiedenen Woertern.
     *
     * <p>Doppelte Eintraege waeren fuer eine Index-Aufgabe schaedlich: Stehen an zwei
     * benachbarten Stellen dieselben Woerter, liefert ein falsch berechneter Index
     * dasselbe Ergebnis wie der richtige, und der Fehler bleibt unsichtbar.</p>
     */
    private String[] zufallsArray(int n) {
        List<String> vorrat = new ArrayList<>(List.of(WOERTER));
        Collections.shuffle(vorrat, random);
        return vorrat.subList(0, Math.min(n, vorrat.size())).toArray(new String[0]);
    }

    private Aufgabe ohneVars(String id, Expr e) {
        return new Aufgabe(id, e, new EvaluationContext(), "");
    }

    private String belegungArray(String[] arr, String name, int wert) {
        StringBuilder sb = new StringBuilder("a = {");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(arr[i]).append("\"");
        }
        return sb.append("}, ").append(name).append(" = ").append(wert).toString();
    }
}