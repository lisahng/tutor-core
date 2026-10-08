package de.lmu.tutor.web;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import de.lmu.tutor.session.Protokolleintrag;

/**
 * Schreibt Protokollzeilen und Bewertungen in die MariaDB des Lehrstuhls.
 *
 * <p>Geschrieben wird ausschliesslich angehaengt, nie geaendert und nie geloescht. Das ist
 * die wichtigste Eigenschaft fuer Studiendaten: Was einmal in der Datenbank steht, kann
 * durch einen spaeteren Fehler nicht mehr verschwinden. Bei Dateien haette eine zweite
 * Sitzung unter demselben Pseudonym die erste ueberschrieben, und das waere erst bei der
 * Auswertung aufgefallen.</p>
 *
 * <p><b>Warum Fehler nicht durchgereicht werden.</b> Faellt die Datenbank mitten im Termin
 * aus, soll die Sitzung weiterlaufen. Die Person sitzt davor und hat schon die Haelfte
 * hinter sich. Das Protokoll liegt ohnehin zusaetzlich im Arbeitsspeicher und laesst sich
 * am Ende der Sitzung als CSV herunterladen, die Daten sind also nicht verloren. Ein
 * Fehler wird deshalb nur vermerkt und auf der Abschlussseite angezeigt, damit die
 * Versuchsleitung ihn bemerkt und die Datei von Hand sichert.</p>
 *
 * <p>Die Bohne entsteht nur, wenn eine Datenquelle eingerichtet ist. Ohne Datenbank laeuft
 * die Anwendung unveraendert weiter und schreibt allein die CSV-Dateien, was fuer die
 * Entwicklung auf dem eigenen Rechner das Uebliche ist.</p>
 */
@Component
public class Protokollspeicher {

    private static final String PROTOKOLL_EINFUEGEN = """
            INSERT INTO protokoll (
                sitzung_id, zeitpunkt, teilnehmer, gruppe, kategorie, ausdruck, belegung,
                referenz, antwort, versuch, korrekt, diagnose, vermutung, erklaert,
                konfidenz, feedback_stufe, historie_kategorie, erfolge_vorher,
                fehler_vorher, dauer_millis
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String BEWERTUNG_EINFUEGEN = """
            INSERT INTO bewertung (
                sitzung_id, zeitpunkt, teilnehmer, gruppe, aufgaben_nummer, kategorie,
                ausdruck, versuche, korrekt, feedback_stufe, diagnose, erklaert, bewertung
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /** Die Verbindung zur Datenbank, oder {@code null}, wenn keine eingerichtet ist. */
    private final JdbcTemplate jdbc;

    /** Die letzte Fehlermeldung, oder leer, solange alles geschrieben werden konnte. */
    private volatile String letzterFehler = "";

    /**
     * @param verfuegbar die Datenquelle, sofern eine eingerichtet ist. Ohne Datenbank bleibt
     *                   dieser Speicher untaetig und die Anwendung laeuft unveraendert
     *                   weiter, was auf dem eigenen Rechner der Normalfall ist.
     */
    public Protokollspeicher(ObjectProvider<JdbcTemplate> verfuegbar) {
        this.jdbc = verfuegbar.getIfAvailable();
    }

    /** Ob eine Datenbank eingerichtet ist. */
    public boolean aktiv() {
        return jdbc != null;
    }

    /** Haengt neue Protokollzeilen an. */
    public void speichereProtokoll(String sitzungId, List<Protokolleintrag> zeilen) {
        if (jdbc == null || zeilen.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(PROTOKOLL_EINFUEGEN, zeilen.stream()
                    .map(z -> new Object[] {
                            sitzungId,
                            Timestamp.from(z.zeitpunkt()),
                            z.teilnehmerId(),
                            z.gruppe().name(),
                            z.kategorieId(),
                            z.ausdruck(),
                            z.belegung(),
                            z.referenz(),
                            z.antwort(),
                            z.versuch(),
                            z.korrekt(),
                            z.diagnose(),
                            z.vermutung(),
                            z.erklaert(),
                            z.konfidenz().name(),
                            z.feedbackStufe(),
                            z.historieKategorie(),
                            z.erfolgeVorher(),
                            z.fehlerVorher(),
                            z.dauerMillis()
                    })
                    .toList());
            letzterFehler = "";
        } catch (RuntimeException datenbankProblem) {
            letzterFehler = beschreibe(datenbankProblem);
        }
    }

    /** Haengt neue Bewertungen an. */
    public void speichereBewertungen(String sitzungId, List<Feedbackbewertung> bewertungen) {
        if (jdbc == null || bewertungen.isEmpty()) {
            return;
        }
        try {
            jdbc.batchUpdate(BEWERTUNG_EINFUEGEN, bewertungen.stream()
                    .map(b -> new Object[] {
                            sitzungId,
                            Timestamp.from(b.zeitpunkt()),
                            b.teilnehmerId(),
                            b.gruppe().name(),
                            b.aufgabenNummer(),
                            b.kategorieId(),
                            b.ausdruck(),
                            b.versuche(),
                            b.korrekt(),
                            b.feedbackStufe(),
                            b.diagnose(),
                            b.erklaert(),
                            b.bewertung()
                    })
                    .toList());
            letzterFehler = "";
        } catch (RuntimeException datenbankProblem) {
            letzterFehler = beschreibe(datenbankProblem);
        }
    }

    /** Ob beim letzten Schreiben etwas schiefging. */
    public boolean hatFehler() {
        return !letzterFehler.isEmpty();
    }

    /** Die letzte Fehlermeldung, fuer die Anzeige auf der Abschlussseite. */
    public String letzterFehler() {
        return letzterFehler;
    }

    /**
     * Zaehlt die Zeilen einer Sitzung in der Datenbank.
     *
     * <p>Dient der Abschlussseite als Bestaetigung. Steht dort dieselbe Zahl wie im
     * Arbeitsspeicher, ist die Sitzung vollstaendig gesichert und das Papier kann weg.</p>
     */
    public int gespeicherteZeilen(String sitzungId) {
        if (jdbc == null) {
            return -1;
        }
        try {
            Integer anzahl = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM protokoll WHERE sitzung_id = ?",
                    Integer.class, sitzungId);
            return anzahl == null ? 0 : anzahl;
        } catch (RuntimeException datenbankProblem) {
            return -1;
        }
    }

    private static String beschreibe(RuntimeException e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return text.length() > 300 ? text.substring(0, 300) + " ..." : text;
    }
}
