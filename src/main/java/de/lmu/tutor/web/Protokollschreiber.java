package de.lmu.tutor.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * Sichert das Protokoll einer Sitzung nach jeder Antwort auf die Festplatte.
 *
 * <p>Das Protokoll liegt waehrend der Sitzung im Arbeitsspeicher. Schliesst jemand aus
 * Versehen das Browserfenster oder stuerzt der Server ab, waeren die Daten dieser Person
 * verloren und die Sitzung muesste wiederholt werden. Deshalb wird nach jeder Antwort die
 * vollstaendige Datei neu geschrieben. Das kostet bei zwanzig Aufgaben nichts und erspart
 * im Zweifel einen ganzen Termin.</p>
 *
 * <p><b>Warum vollstaendig neu statt angehaengt.</b> Anhaengen waere sparsamer, aber eine
 * abgebrochene Schreiboperation koennte eine halbe Zeile hinterlassen, die beim Einlesen in
 * R oder SPSS Aerger macht. Die Datei wird deshalb zunaechst daneben geschrieben und erst
 * dann an ihren Platz verschoben. So ist sie zu jedem Zeitpunkt entweder der alte oder der
 * neue Stand, nie etwas dazwischen.</p>
 *
 * <p><b>Zum Dateinamen.</b> Er besteht nur aus dem Pseudonym, etwa {@code P07.csv}. Namen
 * oder andere personenbezogene Angaben kommen nicht vor, denn die Zuordnung von Pseudonym
 * zu Person wird getrennt von den Logdaten aufbewahrt.</p>
 */
@Component
public class Protokollschreiber {

    /**
     * Schreibt das Protokoll einer Sitzung.
     *
     * <p>Fehler beim Schreiben werden nicht durchgereicht. Eine laufende Sitzung soll nicht
     * abbrechen, weil der Ordner gerade nicht beschreibbar ist, denn das Protokoll steht
     * auch am Ende noch zum Herunterladen bereit.</p>
     *
     * @return der Pfad der geschriebenen Datei, oder {@code null} bei einem Fehler
     */
    public Path schreibe(Path ordner, String teilnehmerId, String csv) {
        try {
            Files.createDirectories(ordner);
            Path ziel = ordner.resolve(dateiname(teilnehmerId));
            Path zwischenstand = ordner.resolve(dateiname(teilnehmerId) + ".teil");
            Files.writeString(zwischenstand, csv, StandardCharsets.UTF_8);
            Files.move(zwischenstand, ziel,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return ziel;
        } catch (IOException | UncheckedIOException nichtSchreibbar) {
            return null;
        }
    }

    /**
     * Macht aus dem Pseudonym einen unbedenklichen Dateinamen.
     *
     * <p>Erlaubt sind Buchstaben, Ziffern, Bindestrich und Unterstrich. Alles andere wird
     * ersetzt, damit eine Eingabe wie {@code ../../etc/passwd} nicht aus dem Ordner
     * herausfuehrt.</p>
     */
    static String dateiname(String teilnehmerId) {
        String sauber = teilnehmerId == null ? "" : teilnehmerId.replaceAll("[^A-Za-z0-9_-]", "_");
        if (sauber.isBlank()) {
            sauber = "unbenannt";
        }
        if (sauber.length() > 40) {
            sauber = sauber.substring(0, 40);
        }
        return sauber + ".csv";
    }
}
