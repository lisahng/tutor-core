package de.lmu.tutor.web;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Die Stellschrauben der Studie, einstellbar in {@code application.properties}.
 *
 * <p>Alles, was zwischen Pilotlauf und Hauptstudie noch wandern koennte, steht hier und
 * nicht im Quelltext. Nach dem Pilotlauf ist vor allem die Arbeitszeit zu pruefen: In der
 * ChomskyTrainer-Studie (Schmutz, Blanchette und Strickroth 2026) wurde niemand in der
 * vorgesehenen Zeit fertig, und das sollte sich hier nicht wiederholen.</p>
 *
 * <p><b>Zum Startwert.</b> Alle Teilnehmenden bekommen denselben Startwert, damit der
 * Generator dieselbe Folge von Ausdruecken erzeugt. Verschiedene Gruppen sehen trotzdem
 * verschiedene Aufgaben, und genau das ist gewollt, denn die Auswahl ist der untersuchte
 * Faktor. Gleich bleibt, dass niemand leichtere oder schwerere Ausdruecke bekommt, weil
 * die Ausdruecke einer Kategorie nach demselben Bauplan entstehen.</p>
 *
 * <p><b>Zur Aufgabenzahl.</b> Sie ist eine Obergrenze, nicht ein Ziel. In der Regel
 * beendet die Zeit die Arbeitsphase, nicht der Zaehler. Die Grenze liegt bewusst hoch,
 * damit niemand vorzeitig an ihr haengen bleibt, denn in der Kontrollbedingung laeuft eine
 * feste Runde durch alle vierzehn Kategorien, und wer zuegig arbeitet, schafft mehr als
 * eine davon.</p>
 */
@ConfigurationProperties(prefix = "tutor")
public class Sitzungseinstellungen {

    /** Hoechstzahl der Aufgaben in der Arbeitsphase. Begrenzend ist normalerweise die Zeit. */
    private int aufgaben = 45;

    /** Zeitbudget der Arbeitsphase in Minuten. */
    private int arbeitszeitMinuten = 25;

    /** Versuche je Aufgabe, bevor die Loesung gezeigt wird. */
    private int versuche = 3;

    /** Startwert des Aufgabengenerators, fuer alle Teilnehmenden derselbe. */
    private long seed = 2026L;

    /** Ordner fuer die CSV-Protokolle, relativ zum Projektverzeichnis. */
    private String protokollOrdner = "protokolle";

    /**
     * Ob zusaetzlich CSV-Dateien geschrieben werden.
     *
     * <p>Mit Datenbank bleibt das eingeschaltet, denn eine zweite, unabhaengige Kopie
     * kostet nichts und rettet den Termin, falls die Datenbank ausfaellt. Im Docker-Betrieb
     * muss der Ordner dann auf einem Volume liegen, sonst verschwindet er mit dem
     * Container.</p>
     */
    private boolean csvSchreiben = true;

    /**
     * Schluessel fuer die Export-Adressen.
     *
     * <p>Die Uebungsseiten sind bewusst frei zugaenglich, jeder mit dem Link soll ueben
     * koennen. Der Export aller Studiendaten ist etwas anderes: Ohne Schluessel koennte ihn
     * jeder herunterladen, der die Adresse kennt. Ein leerer Wert gibt den Export frei, was
     * nur auf dem eigenen Rechner sinnvoll ist.</p>
     */
    private String exportSchluessel = "";

    public Sitzungsplan alsPlan() {
        return new Sitzungsplan(aufgaben, Duration.ofMinutes(arbeitszeitMinuten), versuche);
    }

    public int getAufgaben() {
        return aufgaben;
    }

    public void setAufgaben(int aufgaben) {
        this.aufgaben = aufgaben;
    }

    public int getArbeitszeitMinuten() {
        return arbeitszeitMinuten;
    }

    public void setArbeitszeitMinuten(int arbeitszeitMinuten) {
        this.arbeitszeitMinuten = arbeitszeitMinuten;
    }

    public int getVersuche() {
        return versuche;
    }

    public void setVersuche(int versuche) {
        this.versuche = versuche;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public String getProtokollOrdner() {
        return protokollOrdner;
    }

    public void setProtokollOrdner(String protokollOrdner) {
        this.protokollOrdner = protokollOrdner;
    }

    public boolean isCsvSchreiben() {
        return csvSchreiben;
    }

    public void setCsvSchreiben(boolean csvSchreiben) {
        this.csvSchreiben = csvSchreiben;
    }

    public String getExportSchluessel() {
        return exportSchluessel;
    }

    public void setExportSchluessel(String exportSchluessel) {
        this.exportSchluessel = exportSchluessel;
    }

    /** Prueft den mitgeschickten Schluessel. Ein leerer eingestellter Wert gibt frei. */
    public boolean schluesselStimmt(String angeboten) {
        if (exportSchluessel.isBlank()) {
            return true;
        }
        return exportSchluessel.equals(angeboten);
    }
}
