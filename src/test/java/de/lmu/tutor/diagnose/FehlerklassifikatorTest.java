package de.lmu.tutor.diagnose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.boolLit;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.cast;
import static de.lmu.tutor.ast.AST.doubleLit;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.postInc;
import static de.lmu.tutor.ast.AST.preInc;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Tests fuer Schritt 5: Vergleichsmodul und Fehlerklassifikator.
 *
 * <p>Geprueft wird, ob eine typische Fehleingabe der richtigen Bug-Library-Kategorie
 * zugeordnet wird. Das ist genau die Funktion, deren Trefferquote in der
 * Evaluationsstudie gemessen werden soll.</p>
 *
 * <p>Die IDs folgen der Nummerierung aus Anhang A.1 der Arbeit.</p>
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

    // ================================================================
    // Vergleichsmodul
    // ================================================================

    @Test
    void korrekteAntwortWirdAlsKorrektErkannt() {
        Aufgabe a = aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))));
        assertTrue(diagnose(a, NutzerAntwort.wert(Value.ofInt(11))).korrekt());
    }

    @Test
    void nichtAuswertbarKorrektErkanntIstKorrekt() {
        Aufgabe a = aufgabe("B08", bin("+", intLit(5), boolLit(true)));
        assertTrue(diagnose(a, NutzerAntwort.nichtAuswertbar()).korrekt());
    }

    @Test
    void vergleichsmodulTrenntWertUndTyp() {
        EvaluationResult referenz = evaluator.evaluate(bin("/", intLit(5), intLit(2)), new EvaluationContext());
        Vergleichsergebnis v = vergleichsmodul.vergleiche(referenz, NutzerAntwort.wert(Value.ofDouble(2.0)));
        assertTrue(v.auswertbarkeitKorrekt());
        assertTrue(v.wertKorrekt());
        assertFalse(v.typKorrekt());
        assertFalse(v.korrekt());
    }

    // ================================================================
    // Exakte Signaturen
    // ================================================================

    @Test
    void b07WennNurDerDatentypFalschIst() {
        // 5 / 2 ergibt 2 : int; Antwort 2.0 : double
        erwarteKategorie("B07", aufgabe("B02", bin("/", intLit(5), intLit(2))),
                NutzerAntwort.wert(Value.ofDouble(2.0)));
    }

    @Test
    void b08WennNichtAuswertbarerAusdruckMitWertBeantwortetWird() {
        erwarteKategorie("B08", aufgabe("B08", bin("+", intLit(5), boolLit(true))),
                NutzerAntwort.wert(Value.ofInt(6)));
    }

    @Test
    void b09WennKurzschlussAusdruckFaelschlichAlsNichtAuswertbarGilt() {
        // false && (1 / 0 == 0) ist dank Kurzschluss auswertbar und ergibt false
        Expr e = bin("&&", boolLit(false), bin("==", bin("/", intLit(1), intLit(0)), intLit(0)));
        erwarteKategorie("B09", aufgabe("B09", e), NutzerAntwort.nichtAuswertbar());
    }

    @Test
    void b13WennZeichenAlsStringAngegebenWird() {
        // "Java".charAt(0) ergibt 'J' : char; Antwort "J" : String
        erwarteKategorie("B13", aufgabe("B13", call(stringLit("Java"), "charAt", intLit(0))),
                NutzerAntwort.wert(Value.ofString("J")));
    }

    // ================================================================
    // Simulierte Fehlregeln
    // ================================================================

    @Test
    void b01WennLinksNachRechtsGerechnetWurde() {
        // 3 + 4 * 2 ergibt 11; typischer Fehler (3 + 4) * 2 = 14
        erwarteKategorie("B01", aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(14)));
    }

    @Test
    void b02WennGanzzahldivisionUebersehenWurde() {
        // 20 / 3 ergibt 6 : int; typischer Fehler 6.666... : double
        erwarteKategorie("B02", aufgabe("B02", bin("/", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofDouble(20 / 3.0)));
    }

    @Test
    void b03WennStringKonkatenationAlsAdditionGerechnetWurde() {
        // "17" + 4 ergibt "174" : String; typischer Fehler 21 : int
        erwarteKategorie("B03", aufgabe("B03", bin("+", stringLit("17"), intLit(4))),
                NutzerAntwort.wert(Value.ofInt(21)));
    }

    @Test
    void b04WennDerCastAlsRundungGelesenWurde() {
        // (int) 2.7 ergibt 2; typischer Fehler 3
        erwarteKategorie("B04", aufgabe("B04", cast(JType.INT, doubleLit(2.7))),
                NutzerAntwort.wert(Value.ofInt(3)));
    }

    @Test
    void b04WennDieDoublePromotionUebersehenWurde() {
        // 7 / 2.0 ergibt 3.5 : double; typischer Fehler 3 : int
        erwarteKategorie("B04", aufgabe("B04", bin("/", intLit(7), doubleLit(2.0))),
                NutzerAntwort.wert(Value.ofInt(3)));
    }

    @Test
    void b05WennDerIndexVersatzIgnoriertWurde() {
        // a[k + 1] mit k = 1 ergibt "blau"; typischer Fehler a[1] = "gelb"
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau", "gruen"})
                .setzeInt("k", 1);
        erwarteKategorie("B05", aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx),
                NutzerAntwort.wert(Value.ofString("gelb")));
    }

    @Test
    void b06WennDasAeussereGliedUebersehenWurde() {
        // "HEY".substring(1).toLowerCase() ergibt "ey"; Fehler ohne toLowerCase: "EY"
        erwarteKategorie("B06",
                aufgabe("B06", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase")),
                NutzerAntwort.wert(Value.ofString("EY")));
    }

    @Test
    void b06WennDasInnereGliedUebersehenWurde() {
        // Fehler ohne substring: "hey"
        erwarteKategorie("B06",
                aufgabe("B06", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase")),
                NutzerAntwort.wert(Value.ofString("hey")));
    }

    @Test
    void b10WennModuloAlsQuotientGerechnetWurde() {
        // 20 % 3 ergibt 2; typischer Fehler 6
        erwarteKategorie("B10", aufgabe("B10", bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(6)));
    }

    @Test
    void b11WennDerFalscheZweigGewaehltWurde() {
        // (x > 0) ? 4 : 7 mit x = -3 ergibt 7; Fehler: 4
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", -3);
        erwarteKategorie("B11",
                aufgabe("B11", ternary(bin(">", var("x"), intLit(0)), intLit(4), intLit(7)), ctx),
                NutzerAntwort.wert(Value.ofInt(4)));
    }

    @Test
    void b12WennPostfixWiePraefixGelesenWurde() {
        // x++ mit x = 5 ergibt 5; typischer Fehler 6
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", 5);
        erwarteKategorie("B12", aufgabe("B12", postInc(var("x")), ctx),
                NutzerAntwort.wert(Value.ofInt(6)));
    }

    @Test
    void b12WennPraefixWiePostfixGelesenWurde() {
        // ++x mit x = 5 ergibt 6; typischer Fehler 5
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", 5);
        erwarteKategorie("B12", aufgabe("B12", preInc(var("x")), ctx),
                NutzerAntwort.wert(Value.ofInt(5)));
    }

    @Test
    void b14WennEqualsAlsReferenzvergleichGedeutetWurde() {
        // "haus".equals("haus") ergibt true; Fehler: false
        erwarteKategorie("B14", aufgabe("B14", call(stringLit("haus"), "equals", stringLit("haus"))),
                NutzerAntwort.wert(Value.ofBool(false)));
    }

    @Test
    void b14NurWennDieInhalteUeberhauptGleichSind() {
        // Bei verschiedenen Inhalten liefern beide Deutungen false, der Fehler waere nicht
        // von der richtigen Antwort zu unterscheiden.
        Aufgabe a = aufgabe("B14", call(stringLit("haus"), "equals", stringLit("baum")));
        assertTrue(diagnose(a, NutzerAntwort.wert(Value.ofBool(false))).korrekt());
    }

    // ================================================================
    // Rueckfallstufen
    // ================================================================

    @Test
    void unerklaerbarerWertFaelltAufDieZielkategorieZurueck() {
        Diagnose d = diagnose(aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(99)));
        assertEquals("B01", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.ZIELKATEGORIE, d.konfidenz());
    }

    @Test
    void ohneZielkategorieGreiftDieStrukturVermutung() {
        Diagnose d = diagnose(aufgabe(null, bin("%", intLit(20), intLit(3))),
                NutzerAntwort.wert(Value.ofInt(99)));
        assertEquals("B10", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.VERMUTET, d.konfidenz());
    }

    @Test
    void simulierteRegelSchlaegtZielkategorie() {
        // Die Aufgabe wurde fuer B07 erzeugt, der Wert entspricht aber exakt der Regel B01.
        Diagnose d = diagnose(aufgabe("B07", bin("+", intLit(3), bin("*", intLit(4), intLit(2)))),
                NutzerAntwort.wert(Value.ofInt(14)));
        assertEquals("B01", d.misconception().orElseThrow().id());
        assertEquals(Diagnose.Konfidenz.SIMULIERT, d.konfidenz());
    }
}