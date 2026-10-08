package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.session.Teilnehmer;

/**
 * Tests fuer alles, was mit dem Speichern der Studiendaten zu tun hat.
 *
 * <p>Die Datenbankzugriffe selbst lassen sich hier nicht pruefen, dafuer braeuchte es eine
 * laufende MariaDB. Geprueft wird das, woran es erfahrungsgemaess scheitert: dass nach
 * jeder Antwort genau die neuen Zeilen herausgegeben werden und keine zweimal, dass zwei
 * gleichzeitige Sitzungen auseinanderzuhalten sind, und dass der Export nicht ohne
 * Schluessel herausgeht.</p>
 */
class DatenhaltungTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private Uebungsablauf gestartet() {
        Uebungsablauf a = new Uebungsablauf(lib, new Teilnehmer("P07", Teilnehmer.Gruppe.TUTOR),
                new Sitzungsplan(5, Duration.ofMinutes(25), 3), 2026L, Clock.systemUTC());
        a.starte();
        return a;
    }

    private void falsch(Uebungsablauf a) {
        a.antworte("int", "-987654");
    }

    // ================================================================
    // Die Sitzungskennung
    // ================================================================

    @Test
    void jedeSitzungHatEineEigeneKennung() {
        // Seit mehrere Sitzungen gleichzeitig laufen koennen, genuegt das Pseudonym nicht
        // mehr. Wird versehentlich zweimal dasselbe vergeben, muessen sich die beiden
        // Sitzungen in den Daten trotzdem trennen lassen.
        assertNotEquals(gestartet().sitzungId(), gestartet().sitzungId());
    }

    @Test
    void dieKennungBleibtWaehrendDerSitzungGleich() {
        Uebungsablauf a = gestartet();
        String vorher = a.sitzungId();
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter(3);
        assertEquals(vorher, a.sitzungId());
    }

    // ================================================================
    // Was noch nicht gespeichert ist
    // ================================================================

    @Test
    void eineZeileWirdNurEinmalHerausgegeben() {
        // In die Datenbank wird ausschliesslich angehaengt. Wuerde jedes Mal das ganze
        // Protokoll zurueckkommen, stuende jede Zeile dort mehrfach.
        Uebungsablauf a = gestartet();
        falsch(a);
        assertEquals(1, a.neueProtokollzeilen().size());
        assertTrue(a.neueProtokollzeilen().isEmpty());

        falsch(a);
        assertEquals(1, a.neueProtokollzeilen().size());
        assertTrue(a.neueProtokollzeilen().isEmpty());
    }

    @Test
    void mehrereZeilenAufEinmalWerdenVollstaendigHerausgegeben() {
        // Faellt die Datenbank zwischendurch aus und kommt wieder, muessen alle
        // zwischenzeitlich entstandenen Zeilen nachgereicht werden.
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        falsch(a);
        assertEquals(3, a.neueProtokollzeilen().size());
    }

    @Test
    void eineUnlesbareEingabeErzeugtKeineNeueZeile() {
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        assertTrue(a.neueProtokollzeilen().isEmpty());
    }

    @Test
    void bewertungenWerdenEbenfallsNurEinmalHerausgegeben() {
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter(4);

        assertEquals(1, a.neueBewertungen().size());
        assertTrue(a.neueBewertungen().isEmpty());
    }

    @Test
    void dasVollstaendigeProtokollBleibtTrotzdemAbrufbar() {
        // Die Abschlussseite und der Download je Sitzung brauchen weiterhin alles.
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        a.neueProtokollzeilen();
        assertEquals(2, a.protokoll().size());
    }

    // ================================================================
    // Der Schutz des Exports
    // ================================================================

    @Test
    void ohneEingestelltenSchluesselIstDerExportFrei() {
        // Nur auf dem eigenen Rechner vertretbar, deshalb steht im Container ein Wert.
        Sitzungseinstellungen e = new Sitzungseinstellungen();
        assertTrue(e.schluesselStimmt(null));
        assertTrue(e.schluesselStimmt("irgendwas"));
    }

    @Test
    void mitSchluesselGehtNurDerRichtigeDurch() {
        Sitzungseinstellungen e = new Sitzungseinstellungen();
        e.setExportSchluessel("geheim");

        assertTrue(e.schluesselStimmt("geheim"));
        assertFalse(e.schluesselStimmt("Geheim"));
        assertFalse(e.schluesselStimmt(""));
        assertFalse(e.schluesselStimmt(null));
    }

    // ================================================================
    // Der CSV-Export aus der Datenbank
    // ================================================================

    @Test
    void derExportSchreibtKopfzeileUndZeilen() {
        List<Map<String, Object>> zeilen = List.of(
                zeile("id", 1, "teilnehmer", "P07"),
                zeile("id", 2, "teilnehmer", "P08"));

        String csv = ExportController.alsCsv(zeilen);
        assertEquals("\"id\";\"teilnehmer\"", csv.lines().findFirst().orElseThrow());
        assertEquals(3, csv.lines().count());
        assertTrue(csv.contains("\"P07\""));
    }

    @Test
    void derExportSchuetztTrennzeichenUndAnfuehrungszeichen() {
        // In den Ausdruecken kommen beide vor, etwa bei "33" + 2 oder bei Array-Belegungen.
        String csv = ExportController.alsCsv(List.of(
                zeile("ausdruck", "\"33\" + 2", "belegung", "a = {1; 2}")));

        assertTrue(csv.contains("\"\"\"33\"\" + 2\""));
        assertTrue(csv.contains("\"a = {1; 2}\""));
    }

    @Test
    void einLeeresFeldWirdZurLeerenZelle() {
        String csv = ExportController.alsCsv(List.of(zeile("diagnose", null, "id", 1)));
        assertTrue(csv.contains("\"\""));
    }

    @Test
    void ohneZeilenEntstehtEineLeereDatei() {
        assertEquals("", ExportController.alsCsv(List.of()));
    }

    private Map<String, Object> zeile(String s1, Object w1, String s2, Object w2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(s1, w1);
        m.put(s2, w2);
        return m;
    }
}
