package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.session.Teilnehmer;

/**
 * Tests fuer den Ablauf einer Uebungssitzung im Browser.
 *
 * <p>Geprueft wird das, was erst durch die Weboberflaeche entsteht: die Zustandsfolge einer
 * Aufgabe, die beiden Abbruchbedingungen und der Umgang mit unlesbaren Eingaben. Was
 * inhaltlich passiert, also Diagnose, Feedback und Aufgabenauswahl, ist in
 * {@code UebungssitzungTest} und {@code KontrollbedingungTest} geprueft und wird hier nicht
 * wiederholt.</p>
 *
 * <p>Die Uhr wird gestellt statt gewartet. Ein Test, der 25 Minuten dauert, wird nicht
 * ausgefuehrt und schuetzt deshalb vor gar nichts.</p>
 */
class UebungsablaufTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    /** Eine Uhr, die nur dann weiterlaeuft, wenn der Test sie weiterstellt. */
    private static final class Pruefuhr extends Clock {
        private Instant jetzt = Instant.parse("2026-10-08T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }

        void weiter(Duration d) {
            jetzt = jetzt.plus(d);
        }
    }

    private final Pruefuhr uhr = new Pruefuhr();

    /** Eine garantiert falsche Antwort, als Typ und Wert. */
    private static final String FALSCH_TYP = "int";
    private static final String FALSCH_WERT = "-987654";

    /** Kuerzel fuer "antworte mit einer garantiert falschen Eingabe". */
    private static boolean falsch(Uebungsablauf a) {
        return a.antworte(FALSCH_TYP, FALSCH_WERT);
    }

    private Uebungsablauf ablauf(Sitzungsplan plan, Teilnehmer.Gruppe gruppe) {
        return new Uebungsablauf(lib, new Teilnehmer("P01", gruppe), plan, 2026L, uhr);
    }

    private Uebungsablauf gestartet() {
        Uebungsablauf a = ablauf(new Sitzungsplan(3, Duration.ofMinutes(25), 3),
                Teilnehmer.Gruppe.TUTOR);
        a.starte();
        return a;
    }

    // ================================================================
    // Der Sitzungsplan
    // ================================================================

    @Test
    void einPlanOhneAufgabenOderZeitWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sitzungsplan(0, Duration.ofMinutes(5), 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Sitzungsplan(5, Duration.ZERO, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new Sitzungsplan(5, Duration.ofMinutes(5), 0));
    }

    @Test
    void derStandardplanPasstInDenTermin() {
        // 25 Minuten Arbeitsphase, damit in einer Sitzung von 45 bis 60 Minuten noch Zeit
        // fuer Begruessung, Vortest, Fragebogen und Interview bleibt.
        assertEquals(1500, Sitzungsplan.standard().arbeitszeitSekunden());
    }

    // ================================================================
    // Start
    // ================================================================

    @Test
    void vorDemStartGibtEsKeineAufgabe() {
        Uebungsablauf a = ablauf(Sitzungsplan.standard(), Teilnehmer.Gruppe.TUTOR);
        assertEquals(Uebungsablauf.Zustand.BEREIT, a.zustand());
        assertTrue(a.aufgabe().isEmpty());
        assertEquals(1500, a.restsekunden());
    }

    @Test
    void derStartStelltDieErsteAufgabe() {
        Uebungsablauf a = gestartet();
        assertEquals(Uebungsablauf.Zustand.AUFGABE_OFFEN, a.zustand());
        assertEquals(1, a.aufgabenNummer());
        assertFalse(a.ausdruck().isBlank());
    }

    @Test
    void einZweiterStartBleibtWirkungslos() {
        // Sonst wuerde ein Neuladen der Startseite mitten in der Sitzung von vorn beginnen.
        Uebungsablauf a = gestartet();
        falsch(a);
        a.starte();
        assertEquals(1, a.aufgabenNummer());
        assertEquals(1, a.protokoll().size());
    }

    // ================================================================
    // Unlesbare Eingaben
    // ================================================================

    @Test
    void einTippfehlerZaehltNichtAlsVersuch() {
        // Das Scaffolding wuerde sonst eine Stufe weiterruecken und die Fehlerhistorie des
        // Studentenmodells bekaeme einen Eintrag, der nichts ueber den Kenntnisstand sagt.
        Uebungsablauf a = gestartet();
        assertFalse(a.antworte("int", "qqq"));
        assertEquals(0, a.versuche());
        assertTrue(a.protokoll().isEmpty());
        assertEquals(Uebungsablauf.Zustand.AUFGABE_OFFEN, a.zustand());
    }

    @Test
    void einTippfehlerErzeugtEinenHinweis() {
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        assertFalse(a.eingabehinweis().isBlank());
        assertEquals("int", a.letzterTyp());
        assertEquals("qqq", a.letzterWert());
    }

    @Test
    void derHinweisVerschwindetNachEinerLesbarenEingabe() {
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        falsch(a);
        assertTrue(a.eingabehinweis().isBlank());
    }

    // ================================================================
    // Eine Aufgabe von vorn bis hinten
    // ================================================================

    @Test
    void dieStufenSteigenInnerhalbEinerAufgabe() {
        Uebungsablauf a = gestartet();
        falsch(a);
        assertEquals(0, a.letztesErgebnis().orElseThrow().rueckmeldung().stufe());
        falsch(a);
        assertEquals(1, a.letztesErgebnis().orElseThrow().rueckmeldung().stufe());
        falsch(a);
        assertEquals(2, a.letztesErgebnis().orElseThrow().rueckmeldung().stufe());
    }

    @Test
    void nachDemLetztenVersuchIstDieAufgabeErledigt() {
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        assertEquals(Uebungsablauf.Zustand.AUFGABE_OFFEN, a.zustand());
        falsch(a);
        assertEquals(Uebungsablauf.Zustand.AUFGABE_ERLEDIGT, a.zustand());
        assertFalse(a.musterloesung().isBlank());
    }

    @Test
    void eineErledigteAufgabeNimmtKeineAntwortMehrAn() {
        // Schutz gegen ein doppelt abgeschicktes Formular.
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        falsch(a);
        assertFalse(a.antworte("int", "1"));
        assertEquals(3, a.protokoll().size());
    }

    @Test
    void eineRichtigeAntwortBeendetDieAufgabeSofort() {
        Uebungsablauf a = gestartet();
        String[] teile = a.musterloesung().split(" : ");
        a.antworte(teile[1], teile[0]);
        assertEquals(Uebungsablauf.Zustand.AUFGABE_ERLEDIGT, a.zustand());
        assertTrue(a.letztesErgebnis().orElseThrow().korrekt());
        assertEquals(1, a.richtigeAntworten());
    }

    // ================================================================
    // Der Uebergang zur naechsten Aufgabe
    // ================================================================

    @Test
    void dasScaffoldingBeginntBeiJederAufgabeNeu() {
        // Die Vorgabe aus der vierten Besprechung: Stufe 0 fuer jede neue Aufgabe, das
        // Scaffolding gilt nur innerhalb derselben Aufgabe.
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter();

        assertEquals(2, a.aufgabenNummer());
        assertEquals(0, a.versuche());
        falsch(a);
        assertEquals(0, a.letztesErgebnis().orElseThrow().rueckmeldung().stufe());
    }

    @Test
    void dieAnzeigeWirdBeimWeitergehenGeleert() {
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter();
        assertTrue(a.letztesErgebnis().isEmpty());
        assertTrue(a.letzterTyp().isEmpty());
        assertTrue(a.letzterWert().isEmpty());
        assertTrue(a.eingabehinweis().isBlank());
    }

    @Test
    void weiterWirktNurBeiEinerErledigtenAufgabe() {
        Uebungsablauf a = gestartet();
        a.weiter();
        assertEquals(1, a.aufgabenNummer());
        assertEquals(Uebungsablauf.Zustand.AUFGABE_OFFEN, a.zustand());
    }

    // ================================================================
    // Die beiden Abbruchbedingungen
    // ================================================================

    @Test
    void dasAufgabenbudgetBeendetDieArbeitsphase() {
        Uebungsablauf a = ablauf(new Sitzungsplan(2, Duration.ofMinutes(25), 1),
                Teilnehmer.Gruppe.TUTOR);
        a.starte();
        falsch(a);
        a.weiter();
        assertEquals(2, a.aufgabenNummer());
        assertFalse(a.beendet());

        falsch(a);
        a.weiter();
        assertTrue(a.beendet());
    }

    @Test
    void dieZeitBeendetDieArbeitsphase() {
        Uebungsablauf a = ablauf(new Sitzungsplan(20, Duration.ofMinutes(25), 1),
                Teilnehmer.Gruppe.TUTOR);
        a.starte();
        uhr.weiter(Duration.ofMinutes(26));
        assertEquals(0, a.restsekunden());
        assertTrue(a.zeitAbgelaufen());

        falsch(a);
        a.weiter();
        assertTrue(a.beendet());
    }

    @Test
    void dieZeitUnterbrichtNiemandenMittenInEinerAufgabe() {
        // Geprueft wird erst beim Weitergehen. Wer gerade tippt, darf zu Ende tippen,
        // sonst waere die letzte Aufgabe jeder Person systematisch unvollstaendig.
        Uebungsablauf a = ablauf(new Sitzungsplan(20, Duration.ofMinutes(25), 3),
                Teilnehmer.Gruppe.TUTOR);
        a.starte();
        uhr.weiter(Duration.ofMinutes(26));
        assertTrue(falsch(a));
        assertFalse(a.beendet());
    }

    @Test
    void dieRestzeitWirdNieNegativ() {
        Uebungsablauf a = gestartet();
        uhr.weiter(Duration.ofHours(3));
        assertEquals(0, a.restsekunden());
    }

    @Test
    void dieVersuchsleitungKannVorzeitigBeenden() {
        Uebungsablauf a = gestartet();
        a.beende();
        assertTrue(a.beendet());
    }

    // ================================================================
    // Die Gruppen
    // ================================================================

    @Test
    void dieKontrollgruppeBekommtKeineKategorieZuSehen() {
        Uebungsablauf a = ablauf(new Sitzungsplan(20, Duration.ofMinutes(25), 3),
                Teilnehmer.Gruppe.KONTROLLE);
        a.starte();
        falsch(a);
        assertTrue(a.letztesErgebnis().orElseThrow().rueckmeldung().kategorieId().isEmpty());
    }

    @Test
    void beideGruppenBekommenDieselbenAufgaben() {
        // Der Unterschied zwischen den Gruppen ist die Rueckmeldung und sonst nichts.
        Uebungsablauf tutor = ablauf(new Sitzungsplan(5, Duration.ofMinutes(25), 1),
                Teilnehmer.Gruppe.TUTOR);
        Uebungsablauf kontrolle = ablauf(new Sitzungsplan(5, Duration.ofMinutes(25), 1),
                Teilnehmer.Gruppe.KONTROLLE);
        tutor.starte();
        kontrolle.starte();

        for (int i = 0; i < 5; i++) {
            assertEquals(tutor.ausdruck(), kontrolle.ausdruck());
            falsch(tutor);
            falsch(kontrolle);
            tutor.weiter();
            kontrolle.weiter();
        }
    }

    // ================================================================
    // Protokoll
    // ================================================================

    @Test
    void jedeGewerteteAntwortErzeugtGenauEineProtokollzeile() {
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        falsch(a);
        falsch(a);
        assertEquals(2, a.protokoll().size());
    }

    @Test
    void dasCsvHatEineKopfzeile() {
        Uebungsablauf a = gestartet();
        falsch(a);
        String csv = a.protokollAlsCsv();
        assertTrue(csv.startsWith("zeitpunkt;teilnehmer;gruppe;"), "war: " + csv.lines().findFirst());
        assertEquals(2, csv.lines().count());
    }
}
