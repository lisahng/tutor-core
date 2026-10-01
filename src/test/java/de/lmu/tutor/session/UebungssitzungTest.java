package de.lmu.tutor.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.student.PfaParameter;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Tests fuer die Uebungssitzung.
 *
 * <p>Hier wird nicht mehr die einzelne Komponente geprueft, sondern ihr Zusammenspiel:
 * Kommt zu jeder Kategorie eine loesbare Aufgabe zustande, wird eine richtige Antwort als
 * richtig erkannt, werden die Hinweise bei Wiederholung konkreter, und entsteht ein
 * Protokoll, mit dem sich die Studie auswerten laesst.</p>
 *
 * <p>Der feste Seed macht die Aufgaben reproduzierbar. Die korrekte Antwort wird nicht
 * fest verdrahtet, sondern aus der Musterloesung der Sitzung entnommen. Den Ausdruck dafuer
 * erneut auszuwerten waere falsch: Aufgaben mit {@code ++} veraendern die Variablenbelegung
 * und lieferten beim zweiten Durchlauf ein anderes Ergebnis.</p>
 */
class UebungssitzungTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private Uebungssitzung sitzung() {
        return new Uebungssitzung(lib, 42L);
    }

    /** Die korrekte Antwort zur aktuell gestellten Aufgabe, aus der Musterloesung entnommen. */
    private NutzerAntwort korrekteAntwort(Uebungssitzung sitzung) {
        EvaluationResult referenz = sitzung.musterloesung().orElseThrow();
        return referenz.auswertbar()
                ? NutzerAntwort.wert(referenz.wert())
                : NutzerAntwort.nichtAuswertbar();
    }

    /** Eine garantiert falsche Antwort: ein Wert, den keine Aufgabe dieser Art liefert. */
    private NutzerAntwort falscheAntwort() {
        return NutzerAntwort.wert(Value.ofInt(-987654));
    }

    // ---- Grundablauf ----

    @Test
    void vorDerErstenAufgabeGibtEsKeineAufgabe() {
        Uebungssitzung s = sitzung();
        assertTrue(s.aktuelleAufgabe().isEmpty());
        assertTrue(s.protokoll().isEmpty());
    }

    @Test
    void antwortOhneAufgabeIstEinProgrammierfehler() {
        Uebungssitzung s = sitzung();
        assertThrows(IllegalStateException.class, () -> s.antworte(falscheAntwort()));
    }

    @Test
    void naechsteAufgabeLiefertEineGestellteAufgabe() {
        Uebungssitzung s = sitzung();
        Aufgabe a = s.naechsteAufgabe();
        assertFalse(a.render().isBlank());
        assertEquals(a, s.aktuelleAufgabe().orElseThrow());
        assertEquals(0, s.versucheAnAktuellerAufgabe());
    }

    @Test
    void korrekteAntwortWirdAlsKorrektErkannt() {
        Uebungssitzung s = sitzung();
        Aufgabe a = s.naechsteAufgabe();
        Antwortergebnis ergebnis = s.antworte(korrekteAntwort(s));

        assertTrue(ergebnis.korrekt(), "Aufgabe " + a.render() + " wurde falsch bewertet");
        assertTrue(ergebnis.diagnose().misconception().isEmpty());
        assertFalse(ergebnis.text().isBlank());
        assertEquals(1, ergebnis.versuch());
    }

    @Test
    void falscheAntwortErzeugtFeedback() {
        Uebungssitzung s = sitzung();
        s.naechsteAufgabe();
        Antwortergebnis ergebnis = s.antworte(falscheAntwort());

        assertFalse(ergebnis.korrekt());
        assertFalse(ergebnis.text().isBlank());
        assertFalse(ergebnis.referenzText().isBlank());
    }

    @Test
    void jedeKategorieLiefertEineLoesbareAufgabe() {
        // Der entscheidende Integrationstest: Generator, Evaluator und Fassade muessen
        // fuer jede Kategorie der Bug Library zusammenpassen.
        Uebungssitzung s = sitzung();
        for (var kategorie : lib.all()) {
            Aufgabe a = s.aufgabeZu(kategorie.id());
            Antwortergebnis ergebnis = s.antworte(korrekteAntwort(s));
            assertTrue(ergebnis.korrekt(),
                    kategorie.id() + ": " + a.render() + " wurde nicht als korrekt erkannt");
        }
    }

    // ---- Mehrere Versuche und Scaffolding ----

    @Test
    void aufgabeBleibtBisZurNaechstenGestellt() {
        Uebungssitzung s = sitzung();
        Aufgabe a = s.naechsteAufgabe();
        s.antworte(falscheAntwort());
        s.antworte(falscheAntwort());

        assertEquals(a, s.aktuelleAufgabe().orElseThrow());
        assertEquals(2, s.versucheAnAktuellerAufgabe());
        assertEquals(2, s.protokoll().size());
    }

    @Test
    void versuchszaehlerStartetBeiNeuerAufgabeNeu() {
        Uebungssitzung s = sitzung();
        s.naechsteAufgabe();
        s.antworte(falscheAntwort());
        s.naechsteAufgabe();
        assertEquals(0, s.versucheAnAktuellerAufgabe());
    }

    @Test
    void hinweiseWerdenAnDerselbenAufgabeKonkreter() {
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B01");

        Antwortergebnis erst = s.antworte(falscheAntwort());
        Antwortergebnis zweit = s.antworte(falscheAntwort());
        Antwortergebnis dritt = s.antworte(falscheAntwort());

        assertNotEquals(erst.text(), zweit.text());
        assertNotEquals(zweit.text(), dritt.text());
    }

    @Test
    void jedeNeueAufgabeBeginntWiederBeimAllgemeinenHinweis() {
        // Vorgabe aus der Besprechung: Das Scaffolding gilt innerhalb einer Aufgabe.
        // Eine neue Aufgabe ist eine neue Gelegenheit und startet wieder bei Stufe 0,
        // auch wenn dieselbe Kategorie vorher schon mehrfach danebenging.
        Uebungssitzung s = sitzung();

        s.aufgabeZu("B01");
        s.antworte(falscheAntwort());
        s.antworte(falscheAntwort());
        Antwortergebnis letzterVersuch = s.antworte(falscheAntwort());

        s.aufgabeZu("B01");
        Antwortergebnis neueAufgabe = s.antworte(falscheAntwort());

        assertNotEquals(letzterVersuch.text(), neueAufgabe.text(),
                "Die neue Aufgabe muesste wieder beim allgemeinen Hinweis beginnen");
    }

    @Test
    void unerklaerterFehlerNenntKeineKategorie() {
        // Die Eingabe passt zu keiner Fehlregel. Statt eine Fehlvorstellung zu behaupten,
        // soll das Feedback den Loesungsweg abgleichen.
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B01");
        Antwortergebnis ergebnis = s.antworte(falscheAntwort());

        assertFalse(ergebnis.korrekt());
        assertTrue(ergebnis.rueckmeldung().kategorieId().isEmpty(),
                "Eine unbegruendete Diagnose darf keine Kategorie im Feedback nennen");
        assertFalse(ergebnis.text().isBlank());
    }

    @Test
    void dasStudentenmodellZaehltWeiterhinUeberDieGanzeSitzung() {
        // Die Scaffolding-Stufe wird je Aufgabe zurueckgesetzt, die Fehlerhistorie nicht.
        // Fuer die Aufgabenauswahl ueber PFA ist die Historie der ganzen Sitzung noetig.
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B01");
        s.antworte(falscheAntwort());
        s.aufgabeZu("B01");
        s.antworte(falscheAntwort());
        assertEquals(2, s.studentenmodell().versucheGesamt());
    }

    // ---- Studentenmodell ----

    @Test
    void richtigeUndFalscheAntwortenLandenImStudentenmodell() {
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B02");
        s.antworte(korrekteAntwort(s));

        assertEquals(1, s.richtigeAntworten());
        assertEquals(1, s.studentenmodell().versucheGesamt());
        assertEquals(1, s.studentenmodell().erfolge("B02"));
    }

    @Test
    void kenntnisstandDecktDieGanzeBugLibraryAb() {
        Uebungssitzung s = sitzung();
        s.naechsteAufgabe();
        s.antworte(falscheAntwort());
        assertEquals(lib.size(), s.kenntnisstand().size());
    }

    @Test
    void aufeinanderfolgendeAufgabenWiederholenDieKategorieNicht() {
        Uebungssitzung s = sitzung();
        String erste = s.naechsteAufgabe().kategorieId();
        s.antworte(falscheAntwort());
        assertNotEquals(erste, s.naechsteAufgabe().kategorieId());
    }

    @Test
    void eineLaengereSitzungLaeuftDurchUndUebtMehrereKategorien() {
        // Sperre von drei Kategorien, damit die Abwechslung nicht vom Zufall abhaengt.
        // Prueft nebenbei den vollstaendig konfigurierbaren Konstruktor.
        Uebungssitzung s = new Uebungssitzung(lib,
                new AufgabenGenerator(42L),
                new Feedbackgenerator(new Random(42L)),
                new Studentenmodell(lib, PfaParameter.STANDARD, 3),
                Clock.systemDefaultZone());
        Set<String> kategorien = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Aufgabe a = s.naechsteAufgabe();
            kategorien.add(a.kategorieId());
            // Abwechselnd richtig und falsch, damit beide Pfade vorkommen.
            s.antworte(i % 2 == 0 ? korrekteAntwort(s) : falscheAntwort());
        }
        assertEquals(20, s.protokoll().size());
        assertTrue(kategorien.size() >= 4,
                "Zu wenig Abwechslung, nur geuebt: " + kategorien);
    }

    // ---- Protokoll ----

    @Test
    void unerklaerteFehlerStehenNichtInDerDiagnoseSpalte() {
        // Die Eingabe -987654 passt zu keiner Fehlregel. Das System vermutet dann die
        // Zielkategorie, darf sie aber nicht als begruendete Diagnose ausweisen.
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B01");
        s.antworte(falscheAntwort());

        Protokolleintrag eintrag = s.protokoll().get(0);
        assertFalse(eintrag.erklaert());
        assertTrue(eintrag.diagnose().isBlank(), "Diagnose-Spalte muesste leer sein");
        assertEquals("B01", eintrag.vermutung());
        assertEquals(1, s.unerklaerteFehler().size());
    }

    @Test
    void diagnoseUndVermutungSindNiemalsBeideGefuellt() {
        // Die zentrale Invariante: Ein Eintrag ist entweder begruendet oder geraten,
        // niemals beides. Sonst liessen sich die beiden Trefferquoten nicht trennen.
        Uebungssitzung s = sitzung();
        for (int i = 0; i < 20; i++) {
            Aufgabe a = s.naechsteAufgabe();
            s.antworte(i % 3 == 0 ? korrekteAntwort(s) : falscheAntwort());
        }
        for (Protokolleintrag e : s.protokoll()) {
            assertFalse(!e.diagnose().isBlank() && !e.vermutung().isBlank(),
                    "Beide Spalten gefuellt: " + e.alsCsvZeile());
            assertEquals(e.erklaert(), !e.diagnose().isBlank(),
                    "erklaert passt nicht zur Diagnose-Spalte: " + e.alsCsvZeile());
            if (e.korrekt()) {
                assertTrue(e.vermutung().isBlank(), "Richtige Antwort mit Vermutung: " + e.alsCsvZeile());
            }
        }
        assertEquals(s.unerklaerteFehler().size(),
                s.protokoll().stream().filter(e -> !e.korrekt() && !e.erklaert()).count());
    }

    @Test
    void protokollHaeltDieDatenFuerDieAuswertung() {
        Uebungssitzung s = sitzung();
        Aufgabe a = s.aufgabeZu("B05");
        s.antworte(falscheAntwort());

        Protokolleintrag eintrag = s.protokoll().get(0);
        assertEquals("B05", eintrag.kategorieId());
        assertEquals(a.render(), eintrag.ausdruck());
        assertEquals(1, eintrag.versuch());
        assertFalse(eintrag.korrekt());
        assertFalse(eintrag.referenz().isBlank());
        assertTrue(eintrag.dauerMillis() >= 0);
    }

    @Test
    void protokollIstVonAussenNichtVeraenderbar() {
        Uebungssitzung s = sitzung();
        s.naechsteAufgabe();
        s.antworte(falscheAntwort());
        assertThrows(UnsupportedOperationException.class, () -> s.protokoll().clear());
    }

    @Test
    void csvHatKopfzeileUndEineZeileProVersuch() {
        Uebungssitzung s = sitzung();
        s.naechsteAufgabe();
        s.antworte(falscheAntwort());
        s.antworte(falscheAntwort());

        String[] zeilen = s.protokollAlsCsv().split("\\R");
        assertEquals(3, zeilen.length);
        assertTrue(zeilen[0].startsWith("zeitpunkt;"));
    }

    @Test
    void csvVerschiebtKeineSpaltenBeiSonderzeichen() {
        // Array-Belegungen enthalten Kommata und Anfuehrungszeichen.
        Uebungssitzung s = sitzung();
        s.aufgabeZu("B05");
        s.antworte(falscheAntwort());

        String zeile = s.protokoll().get(0).alsCsvZeile();
        assertEquals(14, zaehleSpalten(zeile),
                "Die Zeile hat nicht 14 Spalten: " + zeile);
    }

    /** Zaehlt Spalten unter Beachtung von Anfuehrungszeichen. */
    private int zaehleSpalten(String csvZeile) {
        int spalten = 1;
        boolean inAnfuehrungszeichen = false;
        for (int i = 0; i < csvZeile.length(); i++) {
            char c = csvZeile.charAt(i);
            if (c == '"') {
                inAnfuehrungszeichen = !inAnfuehrungszeichen;
            } else if (c == ';' && !inAnfuehrungszeichen) {
                spalten++;
            }
        }
        return spalten;
    }
}