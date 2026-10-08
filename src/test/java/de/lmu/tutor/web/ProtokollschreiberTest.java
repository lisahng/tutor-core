package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests fuer die Sicherung des Protokolls auf die Festplatte.
 *
 * <p>Hier geht es um Datenverlust. Jede Sitzung kostet eine Person 45 bis 60 Minuten, und
 * wenn ein versehentlich geschlossenes Browserfenster diese Daten mitnimmt, ist der Termin
 * verloren.</p>
 */
class ProtokollschreiberTest {

    private final Protokollschreiber schreiber = new Protokollschreiber();

    @Test
    void dasProtokollLandetUnterDemPseudonym(@TempDir Path ordner) throws IOException {
        Path datei = schreiber.schreibe(ordner, "P07", "kopf\nzeile");

        assertNotNull(datei);
        assertEquals("P07.csv", datei.getFileName().toString());
        assertEquals("kopf\nzeile", Files.readString(datei, StandardCharsets.UTF_8));
    }

    @Test
    void einZweiterSchreibvorgangErsetztDenErsten(@TempDir Path ordner) throws IOException {
        schreiber.schreibe(ordner, "P07", "erster Stand");
        Path datei = schreiber.schreibe(ordner, "P07", "zweiter Stand");

        assertEquals("zweiter Stand", Files.readString(datei, StandardCharsets.UTF_8));
    }

    @Test
    void esBleibtKeineZwischendateiLiegen(@TempDir Path ordner) throws IOException {
        // Die Datei wird daneben geschrieben und dann verschoben. Bliebe die Zwischendatei
        // liegen, waere spaeter unklar, welche der beiden die Daten der Person enthaelt.
        schreiber.schreibe(ordner, "P07", "inhalt");

        try (var eintraege = Files.list(ordner)) {
            assertEquals(1, eintraege.count());
        }
    }

    @Test
    void derOrdnerWirdBeiBedarfAngelegt(@TempDir Path ordner) {
        Path tief = ordner.resolve("protokolle").resolve("pilot");
        assertNotNull(schreiber.schreibe(tief, "P07", "inhalt"));
        assertTrue(Files.exists(tief.resolve("P07.csv")));
    }

    @Test
    void derDateinameFuehrtNichtAusDemOrdnerHeraus() {
        // Eine Eingabe wie "../../etc/passwd" darf nirgendwo anders landen als im
        // Protokollordner. Alles ausser Buchstaben, Ziffern, Bindestrich und Unterstrich
        // wird deshalb ersetzt.
        assertEquals("______etc_passwd.csv", Protokollschreiber.dateiname("../../etc/passwd"));
        assertEquals("P07.csv", Protokollschreiber.dateiname("P07"));
        assertEquals("unbenannt.csv", Protokollschreiber.dateiname(null));
    }

    @Test
    void einFehlerBeimSchreibenBrichtDieSitzungNichtAb(@TempDir Path ordner) throws IOException {
        // Der Ordnerpfad zeigt auf eine Datei, das Anlegen muss also scheitern. Die
        // Oberflaeche soll trotzdem weiterlaufen, denn das Protokoll steht am Ende auch
        // zum Herunterladen bereit.
        Path belegt = ordner.resolve("belegt");
        Files.writeString(belegt, "keine Ordner hier");

        assertEquals(null, schreiber.schreibe(belegt, "P07", "inhalt"));
    }
}
