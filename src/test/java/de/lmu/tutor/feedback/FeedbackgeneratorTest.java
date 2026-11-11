package de.lmu.tutor.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.var;
import static de.lmu.tutor.ast.AST.intLit;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Tests fuer Schritt 6: Feedback-Generator mit gestuftem Scaffolding.
 *
 * <p>Zwei Verhalten werden geprueft. Bei einer begruendeten Diagnose kommen die Texte aus
 * der Bug Library und werden mit jeder Stufe konkreter. Bei einem unerklaerten Fehler
 * wird keine Kategorie behauptet, sondern der Loesungsweg abgeglichen.</p>
 */
class FeedbackgeneratorTest {

    private final BugLibrary lib = BugLibrary.loadDefault();
    private final Feedbackgenerator generator = new Feedbackgenerator();
    private final StepEvaluator evaluator = new StepEvaluator();
    private final Misconception b01 = lib.byId("B01").orElseThrow();

    /** Eine Aufgabe zum Fuellen der Platzhalter: 3 + 4 * 2 ergibt 11 : int. */
    private final Aufgabe aufgabe = new Aufgabe("B01",
            bin("+", intLit(3), bin("*", intLit(4), intLit(2))), new EvaluationContext(), "");

    private EvaluationResult referenz() {
        return evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
    }

    private Diagnose erklaert(Misconception m) {
        return Diagnose.von(m, Diagnose.Konfidenz.SIMULIERT, "Testfall");
    }

    private Diagnose nurVermutet(Misconception m) {
        return Diagnose.von(m, Diagnose.Konfidenz.ZIELKATEGORIE, "Testfall");
    }

    // ================================================================
    // B06 beim einzelnen Aufruf
    // ================================================================

    /** {@code s.equals("Java")} mit {@code s = "Java"} ergibt {@code true : boolean}. */
    private Aufgabe aufrufAufgabe() {
        EvaluationContext ctx = new EvaluationContext().setzeString("s", "Java");
        return new Aufgabe("B14", call(var("s"), "equals", stringLit("Java")), ctx, "s = \"Java\"");
    }

    /**
     * Wer den Empfaenger zurueckgibt, bekommt Texte ueber Rueckgabewerte und nicht die
     * Texte der Bug Library ueber innere und aeussere Methode. Die passen hier nicht,
     * ihre Platzhalter blieben leer, und uebrig bliebe der allgemeine Ausweichtext.
     */
    @Test
    void b06BeimEinzelaufrufSprichtVomRueckgabewert() {
        Aufgabe auf = aufrufAufgabe();
        EvaluationResult ref = evaluator.evaluate(auf.ausdruck(), auf.kontext());
        Misconception b06 = lib.byId("B06").orElseThrow();

        String stufe1 = generator.erstelle(erklaert(b06), 1, auf, ref).text();
        String stufe2 = generator.erstelle(erklaert(b06), 2, auf, ref).text();

        assertTrue(stufe1.contains("equals"), stufe1);
        assertFalse(stufe1.contains("{"), "Platzhalter nicht aufgeloest: " + stufe1);
        assertTrue(stufe2.contains("true : boolean"), stufe2);
        assertTrue(stufe2.contains("\"Java\""), stufe2);
        assertFalse(stufe2.contains("Methode angewendet, das Gesamtergebnis"),
                "Hier duerfen die Kettentexte nicht erscheinen: " + stufe2);
    }

    /** Bei einer echten Kette bleiben die Texte der Bug Library zustaendig. */
    @Test
    void b06BeiEinerKetteBleibtBeiDenBibliothekstexten() {
        Aufgabe kette = new Aufgabe("B06",
                call(call(stringLit("ROT"), "substring", intLit(1)), "toLowerCase"),
                new EvaluationContext(), "");
        EvaluationResult ref = evaluator.evaluate(kette.ausdruck(), kette.kontext());
        Misconception b06 = lib.byId("B06").orElseThrow();

        String stufe2 = generator.erstelle(erklaert(b06), 2, kette, ref).text();
        assertTrue(stufe2.contains("Zuerst wird"), stufe2);
        assertFalse(stufe2.contains("{"), "Platzhalter nicht aufgeloest: " + stufe2);
    }

    /**
     * Der Ausweichtext der ersten Tracing-Stufe sprach frueher von Operanden und vom
     * Zahlenwert. Bei einem Methodenaufruf auf einem String gibt es beides nicht.
     */
    @Test
    void unerklaerterFehlerBeiEinemAufrufRedetNichtVonZahlen() {
        Aufgabe auf = aufrufAufgabe();
        EvaluationResult ref = evaluator.evaluate(auf.ausdruck(), auf.kontext());

        String text = generator.erstelle(Diagnose.unbekannt("Testfall"), 1, auf, ref).text();
        assertFalse(text.contains("Zahlenwert"), text);
        assertFalse(text.contains("Operanden"), text);
        assertFalse(text.contains("{"), "Platzhalter nicht aufgeloest: " + text);
    }

    // ================================================================
    // Richtige Antworten
    // ================================================================

    @Test
    void korrekteAntwortErhaeltLob() {
        Rueckmeldung r = generator.erstelle(Diagnose.korrekteAntwort(), 0);
        assertTrue(r.korrekt());
        assertFalse(r.text().isBlank());
        assertTrue(r.kategorieId().isEmpty());
    }

    // ================================================================
    // Begruendete Diagnose: Scaffolding aus der Bug Library
    // ================================================================

    @Test
    void ersterFehlerErhaeltDieAllgemeinsteStufe() {
        Rueckmeldung r = generator.erstelle(erklaert(b01), 0, aufgabe, referenz());
        assertFalse(r.korrekt());
        assertEquals(0, r.stufe());
        assertEquals(b01.feedbackStufe(0), r.text());
        assertEquals("B01", r.kategorieId().orElseThrow());
    }

    @Test
    void wiederholungFuehrtZuKonkreteremHinweis() {
        Rueckmeldung erst = generator.erstelle(erklaert(b01), 0, aufgabe, referenz());
        Rueckmeldung zweit = generator.erstelle(erklaert(b01), 1, aufgabe, referenz());
        Rueckmeldung dritt = generator.erstelle(erklaert(b01), 2, aufgabe, referenz());

        assertEquals(0, erst.stufe());
        assertEquals(1, zweit.stufe());
        assertEquals(2, dritt.stufe());
        assertNotEquals(erst.text(), zweit.text());
        assertNotEquals(zweit.text(), dritt.text());
    }

    @Test
    void ueberLetzterStufeBleibtEsBeiDerLoesungshilfe() {
        Rueckmeldung r = generator.erstelle(erklaert(b01), 99, aufgabe, referenz());
        assertEquals(b01.anzahlFeedbackStufen() - 1, r.stufe());
        // Die letzte Stufe rechnet am gestellten Ausdruck vor, nennt ihn also.
        assertTrue(r.text().contains("3 + 4 * 2"), r.text());
    }

    @Test
    void letzteStufeNenntDenGestelltenAusdruckUndNichtDasBeispiel() {
        Rueckmeldung r = generator.erstelle(erklaert(b01), 2, aufgabe, referenz());
        assertTrue(r.text().contains("11"), "Das Ergebnis fehlt: " + r.text());
        assertFalse(r.text().contains("{"), "Ungefuellter Platzhalter: " + r.text());
    }

    @Test
    void ohneAufgabeGibtEsStattPlatzhalternDenAusweichtext() {
        // Die Fassung ohne Aufgabenbezug kann die Platzhalter nicht fuellen. Sie darf
        // deshalb niemals den rohen Text mit geschweiften Klammern ausgeben.
        Rueckmeldung r = generator.erstelle(erklaert(b01), 99);
        assertEquals(b01.anzahlFeedbackStufen() - 1, r.stufe());
        assertFalse(r.text().contains("{"), r.text());
        assertFalse(r.text().isBlank());
    }

    @Test
    void loesungshilfeIstImmerDieLetzteStufe() {
        Rueckmeldung r = generator.erstelleLoesungshilfe(b01, aufgabe, referenz());
        assertEquals(b01.anzahlFeedbackStufen() - 1, r.stufe());
        assertFalse(r.text().contains("{"), r.text());
    }

    @Test
    void jedeKategorieLiefertAufJederStufeText() {
        for (Misconception m : lib.all()) {
            for (int stufe = 0; stufe < m.anzahlFeedbackStufen(); stufe++) {
                Rueckmeldung r = generator.erstelle(erklaert(m), stufe, aufgabe, referenz());
                assertFalse(r.text().isBlank(), m.id() + " Stufe " + stufe + " ist leer");
                assertFalse(r.text().contains("{"),
                        m.id() + " Stufe " + stufe + ": " + r.text());
            }
        }
    }

    // ================================================================
    // Unerklaerter Fehler: Abgleich des Loesungswegs
    // ================================================================

    @Test
    void nurVermuteteKategorieWirdNichtGenannt() {
        // Kern der Vorgabe aus der Besprechung: Erklaert keine Fehlregel den Wert, darf
        // das System keine Fehlvorstellung behaupten. Wer sich vertippt hat, soll keine
        // Belehrung ueber ein Konzept bekommen, das er laengst beherrscht.
        Rueckmeldung r = generator.erstelle(nurVermutet(b01), 0, aufgabe, referenz());
        assertFalse(r.korrekt());
        assertTrue(r.kategorieId().isEmpty(), "Eine Vermutung darf keine Kategorie nennen");
        assertFalse(r.text().contains("Operator"), "Der Text verraet die Kategorie: " + r.text());
    }

    @Test
    void derAbgleichWirdMitJedemVersuchKonkreter() {
        Rueckmeldung erst = generator.erstelle(nurVermutet(b01), 0, aufgabe, referenz());
        Rueckmeldung zweit = generator.erstelle(nurVermutet(b01), 1, aufgabe, referenz());
        Rueckmeldung dritt = generator.erstelle(nurVermutet(b01), 2, aufgabe, referenz());

        assertNotEquals(erst.text(), zweit.text());
        assertNotEquals(zweit.text(), dritt.text());
        // Die letzte Stufe zeigt den Auswertungspfad, nennt also den Ausdruck.
        assertTrue(dritt.text().contains("3 + 4 * 2") || dritt.text().contains("4 * 2"), dritt.text());
    }

    @Test
    void ueberLetzterStufeBleibtEsBeimAuswertungspfad() {
        Rueckmeldung dritt = generator.erstelle(nurVermutet(b01), 2, aufgabe, referenz());
        Rueckmeldung spaeter = generator.erstelle(nurVermutet(b01), 20, aufgabe, referenz());
        assertEquals(dritt.text(), spaeter.text());
    }

    @Test
    void unbekannteDiagnoseErhaeltAllgemeinenHinweis() {
        Rueckmeldung r = generator.erstelle(Diagnose.unbekannt("kein Muster gefunden"), 0,
                aufgabe, referenz());
        assertFalse(r.korrekt());
        assertTrue(r.kategorieId().isEmpty());
        assertFalse(r.text().isBlank());
        assertFalse(r.text().contains("{"), r.text());
    }
}