package de.lmu.tutor.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;

/**
 * Tests fuer Schritt 6: Feedback-Generator mit gestuftem Scaffolding.
 *
 * <p>Zentral ist, dass die Hinweise bei wiederholtem Auftreten derselben
 * Fehlvorstellung konkreter werden (allgemein -&gt; Regel -&gt; Loesungsschritt,
 * vgl. Anhang A.1 der Zulassungsarbeit) und dass die letzte Stufe nicht
 * ueberschritten wird.</p>
 */
class FeedbackgeneratorTest {

    private final BugLibrary lib = BugLibrary.loadDefault();
    private final Feedbackgenerator generator = new Feedbackgenerator(new Random(42));
    private final Misconception b01 = lib.byId("B01").orElseThrow();

    @Test
    void korrekteAntwortErhaeltLob() {
        Rueckmeldung r = generator.erstelle(Diagnose.korrekteAntwort(), 0);
        assertTrue(r.korrekt());
        assertFalse(r.text().isBlank());
        assertTrue(r.kategorieId().isEmpty());
    }

    @Test
    void ersterFehlerErhaeltDieAllgemeinsteStufe() {
        Rueckmeldung r = generator.erstelle(
                Diagnose.von(b01, Diagnose.Konfidenz.SIMULIERT, "Testfall"), 0);
        assertFalse(r.korrekt());
        assertEquals(0, r.stufe());
        assertEquals(b01.feedbackStufe(0), r.text());
        assertEquals("B01", r.kategorieId().orElseThrow());
    }

    @Test
    void wiederholungFuehrtZuKonkreteremHinweis() {
        Rueckmeldung erst = generator.erstelle(Diagnose.von(b01, Diagnose.Konfidenz.SIMULIERT, ""), 0);
        Rueckmeldung zweit = generator.erstelle(Diagnose.von(b01, Diagnose.Konfidenz.SIMULIERT, ""), 1);
        Rueckmeldung dritt = generator.erstelle(Diagnose.von(b01, Diagnose.Konfidenz.SIMULIERT, ""), 2);
        assertEquals(0, erst.stufe());
        assertEquals(1, zweit.stufe());
        assertEquals(2, dritt.stufe());
        assertNotEquals(erst.text(), zweit.text());
        assertNotEquals(zweit.text(), dritt.text());
    }

    @Test
    void ueberLetzterStufeBleibtEsBeiDerLoesungshilfe() {
        Rueckmeldung r = generator.erstelle(Diagnose.von(b01, Diagnose.Konfidenz.SIMULIERT, ""), 99);
        assertEquals(b01.anzahlFeedbackStufen() - 1, r.stufe());
        assertEquals(b01.feedbackStufe(b01.anzahlFeedbackStufen() - 1), r.text());
    }

    @Test
    void loesungshilfeIstImmerDieLetzteStufe() {
        Rueckmeldung r = generator.erstelleLoesungshilfe(b01);
        assertEquals(b01.anzahlFeedbackStufen() - 1, r.stufe());
    }

    @Test
    void unbekannteDiagnoseErhaeltAllgemeinenHinweis() {
        Rueckmeldung r = generator.erstelle(Diagnose.unbekannt("kein Muster gefunden"), 0);
        assertFalse(r.korrekt());
        assertTrue(r.kategorieId().isEmpty());
        assertFalse(r.text().isBlank());
    }

    @Test
    void jedeKategorieLiefertAufJederStufeText() {
        for (Misconception m : lib.all()) {
            for (int stufe = 0; stufe < m.anzahlFeedbackStufen(); stufe++) {
                Rueckmeldung r = generator.erstelle(
                        Diagnose.von(m, Diagnose.Konfidenz.SIMULIERT, ""), stufe);
                assertFalse(r.text().isBlank(), m.id() + " Stufe " + stufe + " ist leer");
            }
        }
    }
}