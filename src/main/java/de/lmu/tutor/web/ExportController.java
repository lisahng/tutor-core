package de.lmu.tutor.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gibt die gesammelten Studiendaten als CSV heraus.
 *
 * <p>Waehrend der Studie liegt jede Sitzung in der Datenbank. Fuer die Auswertung in R oder
 * SPSS braucht es daraus zwei Dateien, eine mit allen Versuchen und eine mit allen
 * Bewertungen, ueber alle Teilnehmenden hinweg.</p>
 *
 * <p><b>Warum diese Adressen einen Schluessel verlangen.</b> Die Uebungsseiten sind bewusst
 * frei zugaenglich, jeder mit dem Link soll ueben koennen. Der Export ist etwas anderes:
 * Dort liegen die Daten aller Teilnehmenden beisammen. Ohne Schluessel koennte sie jeder
 * herunterladen, der die Adresse errraet, und das waere auch bei pseudonymen Daten nicht in
 * Ordnung. Der Schluessel steht in {@code application.properties} oder kommt als
 * Umgebungsvariable aus dem Container.</p>
 *
 * <p>Aufruf also etwa:
 * <pre>  /export/protokoll.csv?schluessel=...  </pre>
 */
@RestController
public class ExportController {

    private final Sitzungseinstellungen einstellungen;
    private final JdbcTemplate jdbc;

    public ExportController(Sitzungseinstellungen einstellungen,
                            ObjectProvider<JdbcTemplate> verfuegbar) {
        this.einstellungen = einstellungen;
        this.jdbc = verfuegbar.getIfAvailable();
    }

    /** Alle Versuche aller Sitzungen. */
    @GetMapping("/export/protokoll.csv")
    public ResponseEntity<byte[]> protokoll(
            @RequestParam(name = "schluessel", required = false) String schluessel) {
        return gib("protokoll", "SELECT * FROM protokoll ORDER BY id", schluessel);
    }

    /** Alle Bewertungen der Rueckmeldung aus allen Sitzungen. */
    @GetMapping("/export/bewertungen.csv")
    public ResponseEntity<byte[]> bewertungen(
            @RequestParam(name = "schluessel", required = false) String schluessel) {
        return gib("bewertungen", "SELECT * FROM bewertung ORDER BY id", schluessel);
    }

    private ResponseEntity<byte[]> gib(String name, String abfrage, String schluessel) {
        if (!einstellungen.schluesselStimmt(schluessel)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (jdbc == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        byte[] inhalt = alsCsv(jdbc.queryForList(abfrage)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(inhalt);
    }

    /**
     * Macht aus den Zeilen eine CSV-Datei.
     *
     * <p>Semikolon als Trennzeichen und Felder in Anfuehrungszeichen, genau wie bei den
     * Dateien je Sitzung. So lassen sich beide Quellen mit demselben Einleseskript
     * verarbeiten. Die Spaltennamen kommen aus der Abfrage, die Methode muss also nicht
     * angepasst werden, wenn eine Spalte dazukommt.</p>
     */
    static String alsCsv(List<Map<String, Object>> zeilen) {
        if (zeilen.isEmpty()) {
            return "";
        }
        List<String> spalten = List.copyOf(zeilen.get(0).keySet());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spalten.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(zitiere(spalten.get(i)));
        }
        for (Map<String, Object> zeile : zeilen) {
            sb.append(System.lineSeparator());
            for (int i = 0; i < spalten.size(); i++) {
                if (i > 0) {
                    sb.append(';');
                }
                Object wert = zeile.get(spalten.get(i));
                sb.append(zitiere(wert == null ? "" : String.valueOf(wert)));
            }
        }
        return sb.toString();
    }

    private static String zitiere(String feld) {
        return "\"" + feld.replace("\"", "\"\"") + "\"";
    }
}
