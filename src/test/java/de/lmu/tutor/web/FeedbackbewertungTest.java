package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.session.Teilnehmer;

/**
 * Tests fuer die Bewertung der Rueckmeldung nach jeder Aufgabe.
 *
 * <p>Geprueft wird vor allem, wann die Frage gestellt wird und wann nicht, denn daran
 * haengt die Aussagekraft der Daten. Eine Bewertung von Lob waere keine Bewertung von
 * Feedback, und eine Frage, die nur eine Gruppe bekommt, liesse sich nicht vergleichen.</p>
 */
class FeedbackbewertungTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private static final String FALSCH_TYP = "int";
    private static final String FALSCH_WERT = "-987654";

    private Uebungsablauf ablauf(Teilnehmer.Gruppe gruppe, int versuche) {
        Uebungsablauf a = new Uebungsablauf(lib, new Teilnehmer("P07", gruppe),
                new Sitzungsplan(5, Duration.ofMinutes(25), versuche), 2026L, Clock.systemUTC());
        a.starte();
        return a;
    }

    private void falsch(Uebungsablauf a) {
        a.antworte(FALSCH_TYP, FALSCH_WERT);
    }

    private void richtig(Uebungsablauf a) {
        String[] teile = a.musterloesung().split(" : ");
        a.antworte(teile[1], teile[0]);
    }

    // ================================================================
    // Wann gefragt wird
    // ================================================================

    @Test
    void waehrendEinerOffenenAufgabeWirdNichtGefragt() {
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 3);
        assertFalse(a.fragtNachBewertung());
        falsch(a);
        assertFalse(a.fragtNachBewertung());
    }

    @Test
    void nachAufgebrauchtenVersuchenWirdGefragt() {
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 3);
        falsch(a);
        falsch(a);
        falsch(a);
        assertTrue(a.fragtNachBewertung());
    }

    @Test
    void nachEinerAufAnhiebRichtigenAntwortWirdNichtGefragt() {
        // Wer auf Anhieb richtig liegt, hat nur Lob bekommen. Ein Lob auf seine
        // Hilfestellung hin zu bewerten ergibt keinen Sinn, und die Haelfte der Daten
        // waere Bewertungen von Lob.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 3);
        richtig(a);
        assertFalse(a.fragtNachBewertung());

        a.weiter(5);
        assertTrue(a.bewertungen().isEmpty());
    }

    @Test
    void nachEinerRichtigenAntwortMitVorherigemFehlerWirdGefragt() {
        // Hier gab es eine Rueckmeldung auf einen Fehler, und die hat vermutlich zur
        // Loesung beigetragen. Genau das ist die interessanteste Bewertung.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 3);
        falsch(a);
        richtig(a);
        assertTrue(a.fragtNachBewertung());

        a.weiter(4);
        assertEquals(1, a.bewertungen().size());
        assertTrue(a.bewertungen().get(0).korrekt());
    }

    @Test
    void auchDieKontrollgruppeWirdGefragt() {
        // Sonst gaebe es nichts zu vergleichen. Frage, Aussehen und Zahl der Klicks sind
        // in beiden Gruppen gleich, verschieden ist allein der bewertete Text.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.KONTROLLE, 1);
        falsch(a);
        assertTrue(a.fragtNachBewertung());

        a.weiter(2);
        assertEquals(1, a.bewertungen().size());
        assertEquals(Teilnehmer.Gruppe.KONTROLLE, a.bewertungen().get(0).gruppe());
    }

    // ================================================================
    // Was aufgezeichnet wird
    // ================================================================

    @Test
    void dieZeileHaeltDenZusammenhangFest() {
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 3);
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter(4);

        Feedbackbewertung b = a.bewertungen().get(0);
        assertEquals(4, b.bewertung());
        assertEquals(1, b.aufgabenNummer());
        assertEquals(3, b.versuche());
        assertEquals(2, b.feedbackStufe());
        assertFalse(b.korrekt());
        assertTrue(b.abgegeben());
    }

    @Test
    void eineVermutungStehtNichtInDerDiagnosespalte() {
        // Dieselbe Trennung wie im Systemprotokoll. Stuende die aus der Herkunft der
        // Aufgabe geratene Kategorie in derselben Spalte, waere die Trefferquote
        // geschoent.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 1);
        falsch(a);
        a.weiter(3);

        Feedbackbewertung b = a.bewertungen().get(0);
        assertFalse(b.erklaert());
        assertTrue(b.diagnose().isEmpty());
    }

    @Test
    void ueberspringenWirdAlsNullAufgezeichnet() {
        // Nicht als fehlender Wert, denn dann waere beim Auswerten nicht zu erkennen, ob
        // jemand bewusst nicht bewertet hat oder ob die Zeile verloren ging.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 1);
        falsch(a);
        a.weiter();

        Feedbackbewertung b = a.bewertungen().get(0);
        assertEquals(Feedbackbewertung.UEBERSPRUNGEN, b.bewertung());
        assertFalse(b.abgegeben());
    }

    @Test
    void werteAusserhalbDerSkalaWerdenAlsUebersprungenGewertet() {
        // Kann ueber die Oberflaeche nicht passieren, wohl aber ueber eine von Hand
        // zusammengebaute Anfrage. Dann soll keine 99 in den Daten landen.
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 1);
        falsch(a);
        a.weiter(99);
        assertEquals(Feedbackbewertung.UEBERSPRUNGEN, a.bewertungen().get(0).bewertung());
    }

    // ================================================================
    // Export
    // ================================================================

    @Test
    void dasCsvHatEineKopfzeileUndEineZeileJeAufgabe() {
        Uebungsablauf a = ablauf(Teilnehmer.Gruppe.TUTOR, 1);
        falsch(a);
        a.weiter(5);
        falsch(a);
        a.weiter(1);

        String csv = a.bewertungenAlsCsv();
        assertTrue(csv.startsWith("zeitpunkt;teilnehmer;gruppe;aufgabenNummer;"));
        assertEquals(3, csv.lines().count());
    }

    @Test
    void dieSkalaHatFuenfBeschrifteteStufen() {
        // Fuenf Stufen, jede einzeln benannt. Nur die Endpunkte zu benennen macht die
        // mittleren Stufen auslegungsbeduerftig und die Daten unschaerfer.
        assertEquals(5, Skala.STUFEN.size());
        assertEquals(1, Skala.STUFEN.get(0).wert());
        assertEquals(5, Skala.STUFEN.get(4).wert());
        for (Skala.Stufe stufe : Skala.STUFEN) {
            assertFalse(stufe.text().isBlank());
        }
    }
}
