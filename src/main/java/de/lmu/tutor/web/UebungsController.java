package de.lmu.tutor.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.session.Teilnehmer;
import jakarta.servlet.http.HttpSession;

/**
 * Die Weboberflaeche der Uebungssitzung.
 *
 * <p>Der Controller haelt bewusst keine Fachlogik. Er liest Formularfelder, ruft genau eine
 * Methode auf {@link Uebungsablauf} auf und reicht dessen Zustand an die Vorlage weiter.
 * Alles, was inhaltlich entschieden wird, naemlich welche Aufgabe kommt, welche Diagnose
 * gestellt wird und welche Rueckmeldung erscheint, passiert in der Kernlogik und ist dort
 * auch getestet.</p>
 *
 * <p><b>Zum Ablauf im Browser.</b> Jede Eingabe wird per POST geschickt und danach auf eine
 * GET-Adresse umgeleitet. Ohne diese Umleitung wuerde ein Neuladen der Seite die letzte
 * Antwort ein zweites Mal absenden und das Protokoll verfaelschen.</p>
 *
 * <p><b>Warum die Formularfelder ausdruecklich benannt sind.</b> Spring kann den Namen
 * eines Parameters auch aus dem uebersetzten Code lesen, aber nur wenn beim Uebersetzen
 * die Option {@code -parameters} gesetzt ist. Sie steht in der {@code pom.xml}. Die Namen
 * hier zusaetzlich hinzuschreiben kostet nichts und haelt die Oberflaeche am Laufen, falls
 * jemand spaeter am Build etwas aendert.</p>
 *
 * <p><b>Zur Sitzung.</b> Der {@link Uebungsablauf} liegt in der HTTP-Sitzung, also pro
 * Browser. Zwei Teilnehmende koennen damit gleichzeitig an verschiedenen Rechnern ueben,
 * ohne sich in die Quere zu kommen. Innerhalb einer Sitzung ist immer nur eine Anfrage
 * unterwegs, deshalb braucht der Ablauf keine Synchronisation.</p>
 */
@Controller
public class UebungsController {

    /** Schluessel des Ablaufs in der HTTP-Sitzung. */
    private static final String SCHLUESSEL = "uebungsablauf";

    private final BugLibrary bibliothek;
    private final Sitzungseinstellungen einstellungen;
    private final Protokollschreiber schreiber;
    private final Clock uhr;

    public UebungsController(BugLibrary bibliothek, Sitzungseinstellungen einstellungen,
                             Protokollschreiber schreiber, Clock uhr) {
        this.bibliothek = bibliothek;
        this.einstellungen = einstellungen;
        this.schreiber = schreiber;
        this.uhr = uhr;
    }

    // ================================================================
    // Start
    // ================================================================

    /**
     * Das Startformular, das die Versuchsleitung vor der Sitzung ausfuellt.
     *
     * <p>Auszufuellen sind nur Pseudonym und Gruppe, also genau die beiden Dinge, die sich
     * von Person zu Person unterscheiden. Der Startwert des Generators wird angezeigt, aber
     * nicht zum Aendern angeboten, und als verstecktes Feld mitgeschickt.</p>
     *
     * <p><b>Die Hintertuer.</b> Fuer eigene Probelaeufe laesst sich der Startwert ueber die
     * Adresse setzen, etwa {@code /?seed=99}. Im normalen Ablauf tippt das niemand, und ein
     * versehentlich geaenderter Startwert mitten im Termin ist damit ausgeschlossen.</p>
     */
    @GetMapping("/")
    public String start(@RequestParam(name = "seed", required = false) String seed,
                        HttpSession sitzung, Model model) {
        Uebungsablauf laufend = ablauf(sitzung);
        if (laufend != null) {
            // Nicht still umleiten. Wer die Startseite aufruft, will meist die naechste
            // Person anlegen, und eine stille Umleitung wuerde die neue Sitzung unter
            // Pseudonym und Gruppe der vorigen laufen lassen. Das faellt erst bei der
            // Auswertung auf, und dann ist der Termin verloren.
            model.addAttribute("laufend", laufend.teilnehmer());
            model.addAttribute("beendet", laufend.beendet());
            return "laufende-sitzung";
        }
        model.addAttribute("plan", einstellungen.alsPlan());
        model.addAttribute("seed", startwert(seed));
        return "start";
    }

    /**
     * Legt die Sitzung an und stellt die erste Aufgabe.
     *
     * <p>Die Gruppe wird hier von der Versuchsleitung gesetzt, nicht vom System ausgelost.
     * Bei zwanzig Teilnehmenden ist eine vorab festgelegte Zuteilung der Zufallszuweisung
     * vorzuziehen, weil sie garantiert gleich grosse Gruppen ergibt.</p>
     */
    @PostMapping("/start")
    public String starte(@RequestParam("teilnehmerId") String teilnehmerId,
                         @RequestParam("gruppe") Teilnehmer.Gruppe gruppe,
                         @RequestParam(name = "seed", required = false) String seed,
                         HttpSession sitzung) {
        Uebungsablauf ablauf = new Uebungsablauf(
                bibliothek,
                new Teilnehmer(teilnehmerId.trim(), gruppe),
                einstellungen.alsPlan(),
                startwert(seed),
                uhr);
        ablauf.starte();
        sitzung.setAttribute(SCHLUESSEL, ablauf);
        return "redirect:/uebung";
    }

    // ================================================================
    // Arbeitsphase
    // ================================================================

    /** Die Aufgabenseite. */
    @GetMapping("/uebung")
    public String uebung(HttpSession sitzung, Model model) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return "redirect:/";
        }
        if (ablauf.beendet()) {
            return "redirect:/ende";
        }
        fuelleModell(model, ablauf);
        return "uebung";
    }

    /** Nimmt eine Antwort entgegen. */
    @PostMapping("/antwort")
    public String antworte(@RequestParam(name = "typ", required = false) String typ,
                           @RequestParam(name = "wert", required = false) String wert,
                           HttpSession sitzung) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return "redirect:/";
        }
        if (ablauf.antworte(typ, wert)) {
            sichere(ablauf);
        }
        return "redirect:/uebung";
    }

    /**
     * Geht zur naechsten Aufgabe und nimmt dabei die Bewertung der Rueckmeldung entgegen.
     *
     * <p>Die Bewertung ist zugleich der Weiter-Knopf. Ein Klick statt zweier, und es gibt
     * keine Reihenfolge, in der jemand weitergeht und das Bewerten vergisst. Wer nicht
     * bewerten moechte, nimmt den Link daneben, dann wird {@code 0} aufgezeichnet.</p>
     */
    @PostMapping("/weiter")
    public String weiter(@RequestParam(name = "bewertung", required = false) Integer bewertung,
                         HttpSession sitzung) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return "redirect:/";
        }
        ablauf.weiter(bewertung == null ? Feedbackbewertung.UEBERSPRUNGEN : bewertung);
        sichere(ablauf);
        return ablauf.beendet() ? "redirect:/ende" : "redirect:/uebung";
    }

    /** Bricht die Arbeitsphase ab, etwa wenn die Zeit im Termin knapp wird. */
    @PostMapping("/beenden")
    public String beenden(HttpSession sitzung) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return "redirect:/";
        }
        ablauf.beende();
        sichere(ablauf);
        return "redirect:/ende";
    }

    // ================================================================
    // Abschluss
    // ================================================================

    /** Die Abschlussseite mit der Zusammenfassung und dem Protokoll. */
    @GetMapping("/ende")
    public String ende(HttpSession sitzung, Model model) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return "redirect:/";
        }
        model.addAttribute("ablauf", ablauf);
        model.addAttribute("teilnehmer", ablauf.teilnehmer());
        model.addAttribute("bearbeitet", ablauf.aufgabenNummer());
        model.addAttribute("richtig", ablauf.richtigeAntworten());
        model.addAttribute("zeilen", ablauf.protokoll().size());
        model.addAttribute("bewertungen", ablauf.bewertungen().size());
        model.addAttribute("datei", Path.of(einstellungen.getProtokollOrdner())
                .resolve(Protokollschreiber.dateiname(ablauf.teilnehmer().id())).toAbsolutePath());
        return "ende";
    }

    /** Das Protokoll als CSV-Datei zum Herunterladen. */
    @GetMapping("/protokoll.csv")
    public ResponseEntity<byte[]> protokoll(HttpSession sitzung) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] inhalt = ablauf.protokollAlsCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                        + Protokollschreiber.dateiname(ablauf.teilnehmer().id()) + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(inhalt);
    }

    /** Die Bewertungen als CSV-Datei zum Herunterladen. */
    @GetMapping("/bewertungen.csv")
    public ResponseEntity<byte[]> bewertungen(HttpSession sitzung) {
        Uebungsablauf ablauf = ablauf(sitzung);
        if (ablauf == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] inhalt = ablauf.bewertungenAlsCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                        + Protokollschreiber.dateiname(ablauf.teilnehmer().id(), "bewertungen") + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(inhalt);
    }

    /** Raeumt die Sitzung ab, damit der naechste Termin bei null beginnt. */
    @PostMapping("/neu")
    public String neu(HttpSession sitzung) {
        sitzung.invalidate();
        return "redirect:/";
    }

    // ================================================================
    // Hilfsmittel
    // ================================================================

    /**
     * Liest den Startwert aus dem Formular.
     *
     * <p>Ein leeres oder unsinniges Feld faellt auf den eingestellten Wert zurueck, statt
     * die Anfrage mit einem Fehler abzuweisen. Mitten im Termin ist eine weisse Fehlerseite
     * das Letzte, was jemand gebrauchen kann.</p>
     */
    private long startwert(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) {
            return einstellungen.getSeed();
        }
        try {
            return Long.parseLong(eingabe.trim());
        } catch (NumberFormatException keineZahl) {
            return einstellungen.getSeed();
        }
    }

    private Uebungsablauf ablauf(HttpSession sitzung) {
        return (Uebungsablauf) sitzung.getAttribute(SCHLUESSEL);
    }

    /**
     * Sichert Systemprotokoll und Bewertungen nach jeder Antwort.
     *
     * <p>Zwei Dateien, weil die Einheiten verschieden sind: eine Zeile je Versuch im
     * Protokoll, eine Zeile je Aufgabe bei den Bewertungen.</p>
     */
    private void sichere(Uebungsablauf ablauf) {
        Path ordner = Path.of(einstellungen.getProtokollOrdner());
        String id = ablauf.teilnehmer().id();
        schreiber.schreibe(ordner, id, ablauf.protokollAlsCsv());
        if (!ablauf.bewertungen().isEmpty()) {
            schreiber.schreibe(ordner, id, "bewertungen", ablauf.bewertungenAlsCsv());
        }
    }

    /**
     * Packt alles in das Modell, was die Aufgabenseite anzeigt.
     *
     * <p>Die Rueckmeldung wird einzeln herausgereicht statt die Vorlage auf
     * {@code Optional} zugreifen zu lassen. Vorlagen sollen anzeigen und nicht entscheiden.</p>
     */
    private void fuelleModell(Model model, Uebungsablauf ablauf) {
        model.addAttribute("nummer", ablauf.aufgabenNummer());
        model.addAttribute("gesamt", ablauf.plan().maximaleAufgaben());
        model.addAttribute("restsekunden", ablauf.restsekunden());
        model.addAttribute("ausdruck", ablauf.ausdruck());
        model.addAttribute("belegung", ablauf.belegung());
        model.addAttribute("hinweis", ablauf.eingabehinweis());
        model.addAttribute("typen", ablauf.typen());
        model.addAttribute("nichtAuswertbar", Antwortparser.NICHT_AUSWERTBAR);
        model.addAttribute("letzterTyp", ablauf.letzterTyp());
        model.addAttribute("letzterWert", ablauf.letzterWert());
        model.addAttribute("erledigt", ablauf.zustand() == Uebungsablauf.Zustand.AUFGABE_ERLEDIGT);
        model.addAttribute("versuche", ablauf.versuche());
        model.addAttribute("maxVersuche", ablauf.plan().maximaleVersuche());
        model.addAttribute("teilnehmer", ablauf.teilnehmer());

        ablauf.letztesErgebnis().ifPresent(ergebnis -> {
            model.addAttribute("rueckmeldung", ergebnis.text());
            model.addAttribute("korrekt", ergebnis.korrekt());
            model.addAttribute("stufe", ergebnis.rueckmeldung().stufe());
        });
        // Die Loesung erscheint erst, wenn die Aufgabe erledigt ist.
        if (ablauf.zustand() == Uebungsablauf.Zustand.AUFGABE_ERLEDIGT) {
            model.addAttribute("loesung", ablauf.musterloesung());
        }
        model.addAttribute("frageNachBewertung", ablauf.fragtNachBewertung());
        model.addAttribute("skala", Skala.STUFEN);
        model.addAttribute("frage", Skala.FRAGE);
    }
}
