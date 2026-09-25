package de.lmu.tutor.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import static de.lmu.tutor.ast.AST.bin;
import static de.lmu.tutor.ast.AST.call;
import static de.lmu.tutor.ast.AST.index;
import static de.lmu.tutor.ast.AST.intLit;
import static de.lmu.tutor.ast.AST.stringLit;
import static de.lmu.tutor.ast.AST.ternary;
import static de.lmu.tutor.ast.AST.var;
import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.eval.EvaluationContext;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Tests fuer die Platzhalter der dritten Scaffolding-Stufe.
 *
 * <p>Geprueft wird vor allem, dass die Hinweise vom tatsaechlich gestellten Ausdruck
 * sprechen und nicht vom Beispiel aus der Bug Library. Genau das war vorher der Fehler:
 * Zu 25 / 6 erschien eine Erklaerung ueber 20 / 3.</p>
 */
class PlatzhalterTest {

    private final BugLibrary lib = BugLibrary.loadDefault();
    private final StepEvaluator evaluator = new StepEvaluator();
    private final Feedbackgenerator generator = new Feedbackgenerator();

    private Aufgabe aufgabe(String kategorieId, Expr e) {
        return new Aufgabe(kategorieId, e, new EvaluationContext(), "");
    }

    private Aufgabe aufgabe(String kategorieId, Expr e, EvaluationContext ctx) {
        return new Aufgabe(kategorieId, e, ctx, "");
    }

    private Platzhalter platzhalterZu(Aufgabe a) {
        return new Platzhalter(a, evaluator.evaluate(a.ausdruck(), a.kontext()));
    }

    /** Die letzte Stufe der Kategorie, gefuellt mit den Angaben der Aufgabe. */
    private String letzteStufe(String kategorieId, Aufgabe a) {
        Misconception m = lib.byId(kategorieId).orElseThrow();
        EvaluationResult referenz = evaluator.evaluate(a.ausdruck(), a.kontext());
        return generator.erstelle(
                Diagnose.von(m, Diagnose.Konfidenz.SIMULIERT, ""),
                m.anzahlFeedbackStufen() - 1, a, referenz).text();
    }

    // ---- Allgemeine Platzhalter ----

    @Test
    void ausdruckUndErgebnisWerdenImmerAufgeloest() {
        Platzhalter p = platzhalterZu(aufgabe("B02", bin("/", intLit(25), intLit(6))));
        assertEquals("25 / 6", p.fuelle("{ausdruck}").orElseThrow());
        assertEquals("4 : int", p.fuelle("{ergebnis}").orElseThrow());
        assertEquals("4", p.fuelle("{wert}").orElseThrow());
        assertEquals("int", p.fuelle("{typ}").orElseThrow());
    }

    @Test
    void unbekannterPlatzhalterLiefertNichts() {
        Platzhalter p = platzhalterZu(aufgabe("B02", bin("/", intLit(25), intLit(6))));
        assertTrue(p.fuelle("Hier steht {gibtesnicht}.").isEmpty());
    }

    @Test
    void textOhnePlatzhalterBleibtUnveraendert() {
        Platzhalter p = platzhalterZu(aufgabe("B02", bin("/", intLit(25), intLit(6))));
        assertEquals("Nur Text.", p.fuelle("Nur Text.").orElseThrow());
    }

    // ---- Formabhaengige Platzhalter ----

    @Test
    void binaererOperatorKenntBeideSeiten() {
        Platzhalter p = platzhalterZu(aufgabe("B02", bin("/", intLit(25), intLit(6))));
        assertEquals("25", p.fuelle("{links}").orElseThrow());
        assertEquals("6", p.fuelle("{rechts}").orElseThrow());
    }

    @Test
    void arrayZugriffKenntIndexUndBerechnetenWert() {
        EvaluationContext ctx = new EvaluationContext()
                .setzeStringArray("a", new String[]{"rot", "gelb", "blau"})
                .setzeInt("k", 1);
        Platzhalter p = platzhalterZu(aufgabe("B05", index(var("a"), bin("+", var("k"), intLit(1))), ctx));
        assertEquals("a", p.fuelle("{array}").orElseThrow());
        assertEquals("k + 1", p.fuelle("{index}").orElseThrow());
        assertEquals("2", p.fuelle("{indexWert}").orElseThrow());
    }

    @Test
    void methodenketteKenntBeideGliederUndDasZwischenergebnis() {
        Aufgabe a = aufgabe("B06", call(call(stringLit("HEY"), "substring", intLit(1)), "toLowerCase"));
        Platzhalter p = platzhalterZu(a);
        assertTrue(p.kennt("innereMethode"));
        assertTrue(p.kennt("aeussereMethode"));
        assertEquals("\"EY\"", p.fuelle("{zwischenwert}").orElseThrow());
    }

    @Test
    void ternaererOperatorKenntBedingungUndBeideZweige() {
        EvaluationContext ctx = new EvaluationContext().setzeInt("x", -3);
        Platzhalter p = platzhalterZu(
                aufgabe("B11", ternary(bin(">", var("x"), intLit(0)), intLit(4), intLit(7)), ctx));
        assertEquals("x > 0", p.fuelle("{bedingung}").orElseThrow());
        assertEquals("false", p.fuelle("{bedingungWert}").orElseThrow());
        assertEquals("4", p.fuelle("{dann}").orElseThrow());
        assertEquals("7", p.fuelle("{sonst}").orElseThrow());
    }

    // ---- Zusammenspiel mit dem Feedbackgenerator ----

    @Test
    void dritteStufeNenntDenGestelltenAusdruck() {
        // Der eigentliche Fehler von vorher: Der Hinweis sprach von 20 / 3,
        // gestellt war aber 25 / 6.
        String text = letzteStufe("B02", aufgabe("B02", bin("/", intLit(25), intLit(6))));
        assertTrue(text.contains("25"), "Der Hinweis nennt den Ausdruck nicht: " + text);
        assertTrue(text.contains("6"), "Der Hinweis nennt den Ausdruck nicht: " + text);
        assertFalse(text.contains("20 / 3"), "Der Hinweis nennt noch das alte Beispiel: " + text);
    }

    @Test
    void keineRueckmeldungEnthaeltSichtbareKlammern() {
        // Auch wenn Kategorie und Ausdrucksform nicht zusammenpassen, darf niemals ein
        // ungefuellter Platzhalter nach aussen gelangen.
        Aufgabe a = aufgabe("B01", bin("+", intLit(3), bin("*", intLit(4), intLit(2))));
        for (Misconception m : lib.all()) {
            for (int stufe = 0; stufe < m.anzahlFeedbackStufen(); stufe++) {
                EvaluationResult referenz = evaluator.evaluate(a.ausdruck(), a.kontext());
                String text = generator.erstelle(
                        Diagnose.von(m, Diagnose.Konfidenz.SIMULIERT, ""), stufe, a, referenz).text();
                assertFalse(text.contains("{") || text.contains("}"),
                        m.id() + " Stufe " + stufe + ": " + text);
                assertFalse(text.isBlank());
            }
        }
    }

    @Test
    void ohneAufgabeGibtEsDenAusweichtext() {
        // Die alte Signatur kennt die Aufgabe nicht und darf deshalb keine
        // Platzhalter ausgeben.
        Misconception b02 = lib.byId("B02").orElseThrow();
        String text = generator.erstelle(
                Diagnose.von(b02, Diagnose.Konfidenz.SIMULIERT, ""),
                b02.anzahlFeedbackStufen() - 1).text();
        assertFalse(text.contains("{"));
        assertFalse(text.isBlank());
    }
}