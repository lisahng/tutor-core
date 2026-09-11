package de.lmu.tutor.diagnose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Tests fuer Schritt 5: Vergleichsmodul und Fehlerklassifikator.
 *
 * <p>Geprueft wird, ob eine typische Fehleingabe der jeweils richtigen
 * Bug-Library-Kategorie zugeordnet wird -- also genau die Funktion, deren
 * Trefferquote in der Evaluationsstudie (Strang 3) gemessen werden soll.</p>
 */
class FehlerklassifikatorTest {

    private final BugLibrary lib = BugLibrary.loadDefault();
    private final StepEvaluator evaluator = new StepEvaluator();
    private final Fehlerklassifikator klassifikator = new Fehlerklassifikator(lib);
    private final Vergleichsmodul vergleichsmodul = new Vergleichsmodul();

    // ---- Hilfsmittel ----

    private Aufgabe aufgabe(String kategorieId, Expr e) {
        return new Aufgabe(kategorieId, e, new EvaluationContext(), "");
    }

    private Aufgabe aufgabe(String kategorieId, Expr e, EvaluationContext ctx) {
        return new Aufgabe(kategorieId, e, ctx, "");
    }

    private Diagnose diagnose(Aufgabe a, NutzerAntwort antwort) {
        EvaluationResult referenz = evaluator.evaluate(a.ausdruck(), a.kontext());
        return klassifikator.diagnostiziere(a, referenz, antwort);
    }

    private void erwarteKategorie(String erwarteteId, Aufgabe a, NutzerAntwort antwort) {
        Diagnose d = diagnose(a, antwort);
        assertFalse(d.korrekt(), "Antwort sollte als falsch erkannt werden");
        assertTrue(d.misconception().isPresent(), "Es sollte eine Kategorie diagnostiziert werden");
        assertEquals(erwarteteId, d.misconception().get().id(), "Diagnose: " + d.begruendung());
    }

    // ---- Vergleichsmodul ----

    @Test
    void korrekteAntwortWirdAlsKorrektErkannt() {
        // 3 + 4 * 2 -> 11 : int
        Aufgabe a = aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))));
        assertTrue(diagnose(a, NutzerAntwort.wert(Value.ofInt(11))).korrekt());
    }

    @Test
    void nichtAuswertbarKorrektErkanntIstKorrekt() {
        // 5 + true ist nicht auswertbar; der Lernende kreuzt das richtig an.
        Aufgabe a = aufgabe("B08", bin("+", intLit(5), boolLit(true)));
        assertTrue(diagnose(a, NutzerAntwort.nichtAuswertbar()).korrekt());
    }

    @Test
    void vergleichsmodulTrenntWertUndTyp() {
        // 5 / 2 -> 2 : int; Antwort 2.0 : double hat den richtigen Wert, aber den falschen Typ.
        EvaluationResult referenz = evaluator.evaluate(bin("/", intLit(5), intLit(2)), new EvaluationContext());
        Vergleichsergebnis v = vergleichsmodul.vergleiche(referenz, NutzerAntwort.wert(Value.ofDouble(2.0)));
        assertTrue(v.auswertbarkeitKorrekt());
        assertTrue(v.wertKorrekt());
        assertFalse(v.typKorrekt());
        assertFalse(v.korrekt());
    }

    // ---- Exakte Signaturen ----

    @Test
    void b07WennNurDerDatentypFalschIst() {
        // 5 / 2 -> 2 : int, Antwort: 2.0 : double
        erwarteKategorie("B07", aufgabe("B02", bin("/", intLit(5), intLit(2))),
                NutzerAntwort.wert(Value.ofDouble(2.0)));
    }

    @Test
    void b08WennNichtAuswertbarerAusdruckMitWertBeantwortetWird() {
        erwarteKategorie("B08", aufgabe("B08", bin("+", intLit(5), boolLit(true))),
                NutzerAntwort.wert(Value.ofInt(6)));
    }

    @Test
    void b09WennKurzschlussAusdruckFaelschlichAlsNichtAuswertbarGiltType() {
        // false && (1 / 0 == 0) ist dank Kurzschluss auswertbar (false).
        Expr e = bin("&&", boolLit(false), bin("==", bin("/", intLit(1), intLit(0)), intLit(0)));
        erwarteKategorie("B09", aufgabe("B09", e), NutzerAntwort.nichtAuswertbar());
    }

    @Test
    void b13WennZeichenAlsStringAngegebenWird() {
        // "Java".charAt(0) -> 'J' : char, Antwort: "J" : String
        erwarteKategorie("B13", aufgabe("B13", call(stringLit("Java"), "charAt", intLit(0))),
                NutzerAntwort.wert(Value.ofString("J")));
    }

    // ---- Simulierte Fehlregeln (Perturbationsmodell) ----

    @Test
    void b01WennLinksNachRechtsGerechnetWurde() {
        // 3 + 4 * 2 -> 11; typischer Fehler: (3 + 4) * 2 = 14
        erwarteKategorie("B01", aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(14)));
    }

    @Test
    void b02WennGanzzahldivisionUebersehenWurde() {
        // 20 / 3 -> 6 : int; typischer Fehler: 6.666... : double
        erwarteKategorie("B02", aufgabe("B02", bin("/", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofDouble(20 / 3.0)));
    }

    @Test
    void b04WennModuloAlsQuotientGerechnetWurde() {
        // 20 % 3 -> 2; typischer Fehler: 6 (Quotient statt Rest)
        erwarteKategorie("B04", aufgabe("B04", bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(6)));
    }

    @Test
    void b05WennDerIndexVersatzIgnoriertWurde() {
        // a[k + 1] mit k = 1 -> a[2] = "blau"; typischer Fehler: a[1] = "gelb"
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 1);
        Aufgabe a = aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx);
        erwarteKategorie("B05", a, NutzerAntwort.wert(Value.ofString("gelb")));
    }

    @Test
    void b10WennStringKonkatenationAlsAdditionGerechnetWurde() {
        // "17" + 4 -> "174" : String; typischer Fehler: 21 : int
        erwarteKategorie("B10", aufgabe("B10", bin("+", stringLit("17"), intLit(4))),
                NutzerAntwort.wert(Value.ofInt(21)));
    }

    // ---- Rueckfall-Stufen ----

    @Test
    void unerklaerbarerWertFaelltAufDieZielkategorieZurueck() {
        // 3 + 4 * 2 -> 11; die Antwort 99 laesst sich durch keine Fehlregel erklaeren.
        Diagnose d = diagnose(aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(99)));
        assertEquals("B01", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.ZIELKATEGORIE, d.konfidenz());
    }

    @Test
    void ohneZielkategorieGreiftDieStrukturVermutung() {
        Diagnose d = diagnose(aufgabe(null, bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(99)));
        assertEquals("B04", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.VERMUTET, d.konfidenz());
    }

    @Test
    void simulierteRegelSchlaegtZielkategorie() {
        // Die Aufgabe wurde fuer B07 erzeugt, der abgegebene Wert entspricht aber
        // exakt der Fehlregel B01 -- die konkrete Erklaerung gewinnt.
        Diagnose d = diagnose(aufgabe("B07", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(14)));
        assertEquals("B01", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.SIMULIERT, d.konfidenz());
    }
}