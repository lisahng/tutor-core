package de.lmu.tutor.student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;

/**
 * Tests fuer das Studentenmodell.
 *
 * <p>Geprueft wird dreierlei: dass die Zaehler stimmen, dass die Scaffolding-Stufe
 * korrekt abgeleitet wird, und dass die Aufgabenauswahl tatsaechlich adaptiv ist,
 * also Schwaechen bevorzugt und nicht in einer Kategorie haengen bleibt.</p>
 */
class StudentenmodellTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private Studentenmodell modell() {
        return new Studentenmodell(lib);
    }

    // ---- Zaehlen ----

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
    }

    // ---- Scaffolding-Stufe fuer Schritt 6 ----

    @Test
    void ersterFehlerErgibtStufeNull() {
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
        // Erster Fehler: die Person hatte vorher null Fehler, also Stufe 0.
        assertEquals(0, m.erfasseUndGibWiederholungen("B01", false));
        // Zweiter Fehler: jetzt Stufe 1.
        assertEquals(1, m.erfasseUndGibWiederholungen("B01", false));
        assertEquals(2, m.erfasseUndGibWiederholungen("B01", false));
        assertEquals(3, m.fehler("B01"));
    }

    // ---- PFA ----

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

    // ---- Auswahl ----

    @Test
    void ohneVorwissenWirdDeterministischGewaehlt() {
        // Bei gleichen Startwerten gewinnt die Kategorie, die in der Bug Library zuerst
        // steht. Wichtig ist hier nicht welche, sondern dass die Auswahl reproduzierbar
        // ist: zwei frische Modelle muessen dieselbe Aufgabe stellen.
        String ersteWahl = modell().naechsteKategorie().orElseThrow().id();
        assertEquals(ersteWahl, modell().naechsteKategorie().orElseThrow().id());

        Misconception erwartet = lib.all().stream()
                .min((a, b) -> Double.compare(a.beta(), b.beta()))
                .orElseThrow();
        assertEquals(erwartet.id(), ersteWahl);
    }

    @Test
    void schwaechenWerdenBevorzugt() {
        Studentenmodell m = new Studentenmodell(lib, PfaParameter.STANDARD, 0);
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
        Studentenmodell m = modell();
        String erste = m.naechsteKategorie().orElseThrow().id();
        // Ein Fehler wuerde dieselbe Kategorie sonst erneut nach vorn holen.
        m.erfasseErgebnis(erste, false);
        String zweite = m.naechsteKategorie().orElseThrow().id();
        assertNotEquals(erste, zweite);
    }

    @Test
    void laengereSperreHaeltMehrereKategorienFrei() {
        Studentenmodell m = new Studentenmodell(lib, PfaParameter.STANDARD, 3);
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
        // Sperre laenger als die Bug Library: irgendwann ist alles gesperrt.
        Studentenmodell m = new Studentenmodell(lib, PfaParameter.STANDARD, lib.size() + 5);
        for (int i = 0; i < lib.size() + 3; i++) {
            assertTrue(m.naechsteKategorie().isPresent(),
                    "Es muss immer eine Aufgabe zustande kommen, Durchlauf " + i);
        }
    }

    @Test
    void sperreLaengeNullErlaubtWiederholung() {
        Studentenmodell m = new Studentenmodell(lib, PfaParameter.STANDARD, 0);
        String erste = m.naechsteKategorie().orElseThrow().id();
        m.erfasseErgebnis(erste, false);
        assertEquals(erste, m.naechsteKategorie().orElseThrow().id());
        assertTrue(m.gesperrteKategorien().isEmpty());
    }

    // ---- Protokoll ----

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