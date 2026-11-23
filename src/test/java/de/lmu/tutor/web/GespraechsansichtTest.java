package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.session.Teilnehmer;

/**
 * Tests fuer die Ansicht, die im Abschlussgespraech gezeigt wird.
 *
 * <p>Zwei Dinge muessen stimmen. Erstens die Gruppierung: alles, was zu einer Aufgabe
 * eingegeben wurde, gehoert in einen Block, und die Reihenfolge ist die der Bearbeitung.
 * Zweitens der Inhalt: die Ansicht darf nur das zeigen, was die teilnehmende Person
 * waehrend der Bearbeitung ohnehin gesehen hat.</p>
 */
class GespraechsansichtTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private Uebungsablauf gestartet() {
        Uebungsablauf a = new Uebungsablauf(lib, new Teilnehmer("P11", Teilnehmer.Gruppe.TUTOR),
                new Sitzungsplan(5, Duration.ofMinutes(25), 3), 2026L, Clock.systemUTC());
        a.starte();
        return a;
    }

    private void falsch(Uebungsablauf a) {
        a.antworte("int", "-987654");
    }

    @Test
    void alleVersucheZuEinerAufgabeStehenInEinemBlock() {
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);

        List<Gespraechsansicht.Aufgabenblock> bloecke =
                Gespraechsansicht.ausProtokoll(a.protokoll());

        assertEquals(1, bloecke.size());
        assertEquals(2, bloecke.get(0).eingaben().size());
        assertEquals(1, bloecke.get(0).eingaben().get(0).versuch());
        assertEquals(2, bloecke.get(0).eingaben().get(1).versuch());
    }

    @Test
    void jedeAufgabeBekommtEinenEigenenBlock() {
        Uebungsablauf a = gestartet();
        falsch(a);
        falsch(a);
        falsch(a);
        a.weiter(3);
        falsch(a);

        List<Gespraechsansicht.Aufgabenblock> bloecke =
                Gespraechsansicht.ausProtokoll(a.protokoll());

        assertEquals(2, bloecke.size());
        assertEquals(1, bloecke.get(0).nummer());
        assertEquals(2, bloecke.get(1).nummer());
    }

    @Test
    void eineNichtLesbareEingabeStehtMitImVerlauf() {
        // Sie zaehlt nicht als Versuch, gehoert im Gespraech aber dazu: sonst fehlt die
        // Zeit, die zwischen zwei Versuchen verstrichen ist, ohne erkennbaren Grund.
        Uebungsablauf a = gestartet();
        a.antworte("int", "qqq");
        falsch(a);

        List<Gespraechsansicht.Eingabe> eingaben =
                Gespraechsansicht.ausProtokoll(a.protokoll()).get(0).eingaben();

        assertEquals(2, eingaben.size());
        assertFalse(eingaben.get(0).lesbar());
        assertEquals("nicht lesbar", eingaben.get(0).marke());
        assertEquals("1", eingaben.get(1).marke());
    }

    @Test
    void zuJederEingabeStehtDerGeleseneRueckmeldungstext() {
        Uebungsablauf a = gestartet();
        falsch(a);

        Gespraechsansicht.Eingabe erste =
                Gespraechsansicht.ausProtokoll(a.protokoll()).get(0).eingaben().get(0);

        assertFalse(erste.rueckmeldung().isBlank(),
                "Ohne den Text laesst sich im Gespraech nicht nachvollziehen, worauf reagiert wurde");
    }

    @Test
    void ohneBelegungWirdKeineZeileDafuerAngelegt() {
        Uebungsablauf a = gestartet();
        falsch(a);

        Gespraechsansicht.Aufgabenblock block = Gespraechsansicht.ausProtokoll(a.protokoll()).get(0);
        assertEquals(block.belegung() != null && !block.belegung().isBlank(), block.hatBelegung());
    }

    @Test
    void ohneProtokollEntstehtEineLeereListe() {
        assertTrue(Gespraechsansicht.ausProtokoll(List.of()).isEmpty());
    }
}
