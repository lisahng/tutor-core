package de.lmu.tutor.buglib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests fuer Schritt 1: Laden der Bug Library und Abfragen der Kategorien.
 */
class BugLibraryTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    @Test
    void laedtGenau14Kategorien() {
        assertEquals(14, lib.size());
    }

    @Test
    void alleIdsB01BisB14Vorhanden() {
        for (int i = 1; i <= 14; i++) {
            String id = String.format("B%02d", i);
            assertTrue(lib.byId(id).isPresent(), "Fehlt: " + id);
        }
    }

    @Test
    void jedeKategorieHatPflichtfelder() {
        for (Misconception m : lib.all()) {
            assertNotNull(m.id());
            assertFalse(m.name().isBlank(), m.id() + ": name leer");
            assertFalse(m.beschreibung().isBlank(), m.id() + ": beschreibung leer");
            assertFalse(m.konzept().isBlank(), m.id() + ": konzept leer");
            assertNotNull(m.schwierigkeit());
        }
    }

    @Test
    void jedeKategorieHatMindestensDreiFeedbackStufen() {
        for (Misconception m : lib.all()) {
            assertTrue(m.anzahlFeedbackStufen() >= 3,
                    m.id() + " hat nur " + m.anzahlFeedbackStufen() + " Feedback-Stufen");
        }
    }

    @Test
    void basisgewichtIstPositiv() {
        for (Misconception m : lib.all()) {
            assertTrue(m.basisgewicht() > 0, m.id() + ": basisgewicht muss > 0 sein");
        }
    }

    @Test
    void b05HatUntertyp() {
        Misconception b05 = lib.byId("B05").orElseThrow();
        assertTrue(b05.hatUntertypen());
        assertEquals("B05a", b05.untertypen().get(0).id());
    }

    @Test
    void feedbackStufeIstRobustGegenUeberlauf() {
        Misconception b01 = lib.byId("B01").orElseThrow();
        // Index ueber das Ende hinaus -> letzte (konkreteste) Stufe
        assertEquals(b01.feedbackStufe(b01.anzahlFeedbackStufen() - 1),
                b01.feedbackStufe(999));
    }

    @Test
    void auswahlGewichtFolgtFormel() {
        // B01 hat Basisgewicht 5; w = 5 * (1 + 2 * 0.5) = 10
        double w = lib.auswahlGewicht("B01", 0.5, 2.0);
        assertEquals(10.0, w, 1e-9);
    }

    @Test
    void auswahlGewichtOhneFehlerGleichBasisgewicht() {
        Misconception b01 = lib.byId("B01").orElseThrow();
        assertEquals(b01.basisgewicht(), lib.auswahlGewicht("B01", 0.0, 1.0), 1e-9);
    }

    @Test
    void unbekannteIdHatGewichtNull() {
        assertEquals(0.0, lib.auswahlGewicht("B99", 0.5, 1.0), 1e-9);
    }
}
