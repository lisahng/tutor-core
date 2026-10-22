package de.lmu.tutor.gen;

import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import org.junit.jupiter.api.Test;
import de.lmu.tutor.ast.JType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer Schritt 4: den Aufgaben-Generator. Kernidee: Generator und Evaluator
 * pruefen sich gegenseitig -- jede erzeugte Aufgabe muss loesbar sein (ausser B08).
 */
class AufgabenGeneratorTest {

    private static final String[] KATEGORIEN = {"B01", "B02", "B03", "B04", "B05", "B06",
            "B07", "B08", "B09", "B10", "B11", "B12", "B13", "B14"};

    private final StepEvaluator ev = new StepEvaluator();

    @Test
    void jedeErzeugteAufgabeIstLoesbar() {
        // Fuer jede Kategorie viele Aufgaben erzeugen und pruefen:
        // B08 ist immer nicht auswertbar, alle anderen immer auswertbar.
        for (String kat : KATEGORIEN) {
            AufgabenGenerator gen = new AufgabenGenerator(kat.hashCode());
            for (int i = 0; i < 50; i++) {
                Aufgabe auf = gen.generiere(kat);
                assertEquals(kat, auf.kategorieId());
                EvaluationResult r = ev.evaluate(auf.ausdruck(), auf.kontext());
                if (kat.equals("B08")) {
                    assertFalse(r.auswertbar(), "B08 sollte nicht auswertbar sein: " + auf.render());
                } else {
                    assertTrue(r.auswertbar(), kat + " sollte auswertbar sein: " + auf.render()
                            + " -> " + r.ergebnisText());
                }
            }
        }
    }

    @Test
    void gleicherSeedErzeugtGleicheAufgabe() {
        Aufgabe a = new AufgabenGenerator(123).generiere("B01");
        Aufgabe b = new AufgabenGenerator(123).generiere("B01");
        assertEquals(a.render(), b.render());
    }

    @Test
    void b09KurzschlussVermeidetDenLaufzeitfehler() {
        AufgabenGenerator generator = new AufgabenGenerator(2026L);
        StepEvaluator evaluator = new StepEvaluator();

        for (int i = 0; i < 200; i++) {
            Aufgabe aufgabe = generator.generiere("B09");
            EvaluationResult ergebnis = evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());

            assertTrue(ergebnis.auswertbar(),
                    "Dank Kurzschluss muss der Ausdruck auswertbar sein: " + aufgabe.render());
            assertEquals(JType.BOOL, ergebnis.wert().typ());

            boolean oder = aufgabe.render().contains("||");
            assertEquals(oder, ergebnis.wert().asBool(),
                    "Mit || ergibt sich true, mit && false: " + aufgabe.render());
        }
    }

    @Test
    void unbekannteKategorieWirftFehler() {
        assertThrows(IllegalArgumentException.class,
                () -> new AufgabenGenerator(1).generiere("B99"));
    }
}