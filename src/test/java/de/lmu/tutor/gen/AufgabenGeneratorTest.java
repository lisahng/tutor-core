package de.lmu.tutor.gen;

import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import org.junit.jupiter.api.Test;

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
    void b09KurzschlussVermeidetDivisionDurchNull() {
        // false && (x / 0 == 0) muss false ergeben, nicht abstuerzen
        Aufgabe auf = new AufgabenGenerator(7).generiere("B09");
        EvaluationResult r = ev.evaluate(auf.ausdruck(), auf.kontext());
        assertTrue(r.auswertbar());
        assertEquals("false", r.wert().render());
    }

    @Test
    void unbekannteKategorieWirftFehler() {
        assertThrows(IllegalArgumentException.class,
                () -> new AufgabenGenerator(1).generiere("B99"));
    }
}