package de.lmu.tutor.buglib;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fuer Schritt 1: Bug Library als Daten und PFA-Aufgabenauswahl.
 */
class BugLibraryTest {

    private final BugLibrary lib = BugLibrary.loadDefault();
    private static final double GAMMA = 0.4;
    private static final double RHO = -0.8;

    @Test
    void laedtGenau14Kategorien() {
        assertEquals(14, lib.size());
    }

    @Test
    void alleIdsB01BisB14Vorhanden() {
        for (int i = 1; i <= 14; i++) {
            assertTrue(lib.byId(String.format("B%02d", i)).isPresent(), "Fehlt: B" + i);
        }
    }

    @Test
    void b09IstEnthalten() {
        assertTrue(lib.byId("B09").isPresent());
    }

    @Test
    void jedeKategorieHatPflichtfelder() {
        for (Misconception m : lib.all()) {
            assertFalse(m.name().isBlank(), m.id() + ": name leer");
            assertFalse(m.beschreibung().isBlank(), m.id() + ": beschreibung leer");
            assertFalse(m.konzept().isBlank(), m.id() + ": konzept leer");
            assertTrue(Double.isFinite(m.beta()), m.id() + ": beta ungueltig");
        }
    }

    @Test
    void jedeKategorieHatMindestensDreiFeedbackStufen() {
        for (Misconception m : lib.all()) {
            assertTrue(m.anzahlFeedbackStufen() >= 3, m.id() + " hat < 3 Feedback-Stufen");
        }
    }

    @Test
    void b05HatUntertyp() {
        Misconception b05 = lib.byId("B05").orElseThrow();
        assertTrue(b05.hatUntertypen());
        assertEquals("B05a", b05.untertypen().get(0).id());
    }

    // ---- PFA-Aufgabenauswahl ----

    @Test
    void mehrFehlerSenktErfolgswahrscheinlichkeit() {
        assertTrue(lib.erfolgswahrscheinlichkeit("B05", 0, 2, GAMMA, RHO)
                < lib.erfolgswahrscheinlichkeit("B05", 0, 0, GAMMA, RHO));
    }

    @Test
    void mehrErfolgErhoehtErfolgswahrscheinlichkeit() {
        assertTrue(lib.erfolgswahrscheinlichkeit("B05", 2, 0, GAMMA, RHO)
                > lib.erfolgswahrscheinlichkeit("B05", 0, 0, GAMMA, RHO));
    }

    @Test
    void ohneDatenIstWahrscheinlichkeitEinHalb() {
        assertEquals(0.5, lib.erfolgswahrscheinlichkeit("B01", 0, 0, GAMMA, RHO), 1e-9);
    }

    @Test
    void naechsteKategorieIstDieSchwaechste() {
        Map<String, int[]> stat = new HashMap<>();
        stat.put("B05", new int[]{0, 3});
        stat.put("B01", new int[]{3, 0});
        assertEquals("B05", lib.naechsteKategorie(stat, GAMMA, RHO).orElseThrow().id());
    }

    @Test
    void unbekannteKategorieWirftFehler() {
        assertThrows(IllegalArgumentException.class,
                () -> lib.erfolgswahrscheinlichkeit("B99", 0, 0, GAMMA, RHO));
    }
}