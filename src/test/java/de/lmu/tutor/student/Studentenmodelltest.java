package de.lmu.tutor.student;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;

/**
 * Tests fuer das Studentenmodell.
 *
 * <p>Geprueft wird viererlei: dass die Zaehler stimmen, dass die feste erste Runde
 * eingehalten wird, dass die PFA-Schaetzung sich wie erwartet verschiebt, und dass die
 * Aufgabenauswahl danach tatsaechlich adaptiv ist, also Schwaechen bevorzugt und nicht in
 * einer Kategorie haengen bleibt.</p>
 *
 * <p><b>Zu den beiden Konstruktoren.</b> {@link #modell()} liefert das Standardmodell mit
 * fester erster Runde, so wie es im Betrieb laeuft. {@link #ohneErsteRunde(int)} schaltet
 * sie ab. Jeder Test, der die PFA-Auswahl oder die Wiederholungssperre pruefen will, muss
 * diese zweite Form nehmen, denn solange die feste Runde laeuft, entscheidet sie allein
 * und nicht die Fehlerhistorie.</p>
 */
class StudentenmodellTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    /** Standardmodell: feste erste Runde, Sperre von 1. */
    private Studentenmodell modell() {
        return new Studentenmodell(lib);
    }

    /** Modell ohne feste erste Runde, damit sofort die Fehlerhistorie entscheidet. */
    private Studentenmodell ohneErsteRunde(int sperreLaenge) {
        return new Studentenmodell(lib, PfaParameter.STANDARD, sperreLaenge, List.of());
    }

    // ================================================================
    // Zaehlen
    // ================================================================

    @Test
    void neuesModellIstLeer() {
        Studentenmodell m = modell();
        assertEquals(0, m.erfolge("B01"));
        assertEquals(0, m.fehler("B01"));
        assertEquals(0, m.versucheGesamt());
    }

    @Test
    void erfolgeUndFehlerWerdenGetrenntGezaehlt() {
        Studentenmodell m = modell();
        m.erfasseErgebnis("B01", true);
        m.erfasseErgebnis("B01", false);
        m.erfasseErgebnis("B01", false);
        assertEquals(1, m.erfolge("B01"));
        assertEquals(2, m.fehler("B01"));
        assertEquals(3, m.versucheGesamt());
    }

    @Test
    void kategorienBeeinflussenSichNicht() {
        Studentenmodell m = modell();
        m.erfasseErgebnis("B01", false);
        assertEquals(0, m.fehler("B02"));
    }

    @Test
    void zuruecksetzenLeertAlles() {
        Studentenmodell m = modell();
        m.erfasseErgebnis("B01", false);
        m.naechsteKategorie();
        m.zuruecksetzen();
        assertEquals(0, m.versucheGesamt());
        assertTrue(m.gesperrteKategorien().isEmpty());
        assertTrue(m.inErsterRunde(), "Nach dem Zuruecksetzen beginnt die feste Runde von vorn");
    }

    // ================================================================
    // Fehlerhistorie
    // ================================================================

    @Test
    void ohneFehlerIstDieHistorieLeer() {
        Studentenmodell m = modell();
        assertEquals(0, m.wiederholungen("B01"));
    }

    @Test
    void wiederholungenZaehlenNurFehler() {
        Studentenmodell m = modell();
        m.erfasseErgebnis("B01", true);
        m.erfasseErgebnis("B01", true);
        assertEquals(0, m.wiederholungen("B01"));
        m.erfasseErgebnis("B01", false);
        assertEquals(1, m.wiederholungen("B01"));
    }

    @Test
    void erfasseUndGibWiederholungenLiefertDenStandVorDemVersuch() {
        Studentenmodell m = modell();
        // Erster Fehler: vorher null Fehler.
        assertEquals(0, m.erfasseUndGibWiederholungen("B01", false));
        assertEquals(1, m.erfasseUndGibWiederholungen("B01", false));
        assertEquals(2, m.erfasseUndGibWiederholungen("B01", false));
        assertEquals(3, m.fehler("B01"));
    }

    // ================================================================
    // Feste erste Runde
    // ================================================================

    @Test
    void ersteRundeFolgtDerFestenReihenfolge() {
        // Solange beta ueberall null ist, starten alle Kategorien bei P = 0,5 und die
        // erste Aufgabe waere willkuerlich. Fuer einen fairen Gruppenvergleich arbeitet
        // das Modell deshalb zuerst eine feste Reihenfolge ab.
        Studentenmodell m = modell();
        List<String> erwartet = m.startreihenfolge();
        assertFalse(erwartet.isEmpty());

        for (String id : erwartet) {
            assertTrue(m.inErsterRunde());
            assertEquals(id, m.naechsteKategorie().orElseThrow().id());
            m.erfasseErgebnis(id, false);
        }
        assertFalse(m.inErsterRunde(), "Nach einer vollen Runde endet die feste Reihenfolge");
    }

    @Test
    void zweiModelleStellenDieselbeErsteAufgabe() {
        // Zwei Teilnehmende duerfen sich nicht schon in der Aufgabenfolge unterscheiden,
        // sonst traegt der Gruppenvergleich nicht.
        assertEquals(modell().naechsteKategorie().orElseThrow().id(),
                modell().naechsteKategorie().orElseThrow().id());
    }

    @Test
    void nachDerErstenRundeEntscheidetDieHistorie() {
        Studentenmodell m = modell();
        // Die feste Runde abarbeiten, dabei ueberall Erfolge ausser bei B05.
        for (String id : m.startreihenfolge()) {
            m.naechsteKategorie();
            m.erfasseErgebnis(id, !id.equals("B05"));
        }
        assertFalse(m.inErsterRunde());
        assertEquals("B05", m.naechsteKategorie().orElseThrow().id(),
                "Nach der festen Runde muss die schwaechste Kategorie drankommen");
    }

    @Test
    void leereStartreihenfolgeSchaltetDieErsteRundeAb() {
        Studentenmodell m = ohneErsteRunde(1);
        assertFalse(m.inErsterRunde());
        assertTrue(m.startreihenfolge().isEmpty());
        assertTrue(m.naechsteKategorie().isPresent());
    }

    // ================================================================
    // PFA
    // ================================================================

    @Test
    void ohneGeschaetztesBetaStartenAlleKategorienGleich() {
        // Solange beta nicht kalibriert ist, gilt fuer alle Kategorien der Platzhalter.
        // Jede startet damit bei P = 0,5; die Auswahl richtet sich allein nach der
        // Fehlerhistorie und nicht nach angenommenen Schwierigkeitsunterschieden.
        Studentenmodell m = modell();
        for (Misconception mc : lib.all()) {
            if (mc.beta() == BugLibrary.BETA_PLATZHALTER) {
                assertEquals(0.5, m.erfolgswahrscheinlichkeit(mc.id()), 1e-9,
                        "Kategorie " + mc.id() + " startet nicht bei 0,5");
            }
        }
    }

    @Test
    void erfolgeHebenUndFehlerSenkenDieWahrscheinlichkeit() {
        Studentenmodell m = modell();
        double start = m.erfolgswahrscheinlichkeit("B01");

        m.erfasseErgebnis("B01", true);
        double nachErfolg = m.erfolgswahrscheinlichkeit("B01");
        assertTrue(nachErfolg > start, "Ein Erfolg muss die Wahrscheinlichkeit heben");

        m.erfasseErgebnis("B01", false);
        m.erfasseErgebnis("B01", false);
        m.erfasseErgebnis("B01", false);
        assertTrue(m.erfolgswahrscheinlichkeit("B01") < nachErfolg,
                "Fehler muessen die Wahrscheinlichkeit senken");
    }

    @Test
    void wahrscheinlichkeitBleibtZwischenNullUndEins() {
        Studentenmodell m = modell();
        for (int i = 0; i < 50; i++) {
            m.erfasseErgebnis("B01", false);
        }
        double p = m.erfolgswahrscheinlichkeit("B01");
        assertTrue(p > 0.0 && p < 1.0, "P lag ausserhalb von (0,1): " + p);
    }

    // ================================================================
    // Auswahl nach der ersten Runde
    // ================================================================

    @Test
    void schwaechenWerdenBevorzugt() {
        Studentenmodell m = ohneErsteRunde(0);
        // In allen Kategorien Erfolge sammeln, nur in B05 nicht.
        for (Misconception mc : lib.all()) {
            if (!mc.id().equals("B05")) {
                for (int i = 0; i < 5; i++) {
                    m.erfasseErgebnis(mc.id(), true);
                }
            }
        }
        m.erfasseErgebnis("B05", false);
        assertEquals("B05", m.naechsteKategorie().orElseThrow().id());
    }

    @Test
    void dieselbeKategorieKommtNichtZweimalHintereinander() {
        Studentenmodell m = ohneErsteRunde(1);
        String erste = m.naechsteKategorie().orElseThrow().id();
        // Ein Fehler wuerde dieselbe Kategorie sonst erneut nach vorn holen.
        m.erfasseErgebnis(erste, false);
        assertNotEquals(erste, m.naechsteKategorie().orElseThrow().id());
    }

    @Test
    void laengereSperreHaeltMehrereKategorienFrei() {
        Studentenmodell m = ohneErsteRunde(3);
        Set<String> gesehen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String id = m.naechsteKategorie().orElseThrow().id();
            m.erfasseErgebnis(id, false);
            gesehen.add(id);
        }
        assertEquals(4, gesehen.size(), "Vier Aufgaben, aber Wiederholungen dabei: " + gesehen);
    }

    @Test
    void sperreGreiftNichtWennKeineAlternativeBleibt() {
        // Sperre laenger als die Bug Library: irgendwann ist alles gesperrt. Dann muss die
        // Sperre weichen, sonst kaeme gar keine Aufgabe mehr zustande.
        Studentenmodell m = ohneErsteRunde(lib.size() + 5);
        for (int i = 0; i < lib.size() + 3; i++) {
            assertTrue(m.naechsteKategorie().isPresent(),
                    "Es muss immer eine Aufgabe zustande kommen, Durchlauf " + i);
        }
    }

    @Test
    void sperreLaengeNullErlaubtWiederholung() {
        Studentenmodell m = ohneErsteRunde(0);
        String erste = m.naechsteKategorie().orElseThrow().id();
        m.erfasseErgebnis(erste, false);
        assertEquals(erste, m.naechsteKategorie().orElseThrow().id());
        assertTrue(m.gesperrteKategorien().isEmpty());
    }

    // ================================================================
    // Protokoll
    // ================================================================

    @Test
    void alleStatistikenDeckenDieGanzeBugLibraryAb() {
        Studentenmodell m = modell();
        m.erfasseErgebnis("B01", false);
        assertEquals(lib.size(), m.alleStatistiken().size());

        Kategoriestatistik b01 = m.statistik("B01");
        assertEquals(1, b01.versuche());
        assertEquals(1.0, b01.fehlerquote());
        assertFalse(b01.toString().isBlank());
    }
}