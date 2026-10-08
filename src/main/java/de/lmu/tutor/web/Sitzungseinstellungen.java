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
 * <p><b>Zum Startwert.</b> Alle Teilnehmenden bekommen denselben Startwert, damit auch
 * dieselben Aufgaben erzeugt werden. Unterschiedliche Aufgaben waeren ein zweiter
 * Unterschied zwischen den Gruppen und wuerden den Vergleich tragen lassen, obwohl er es
 * nicht tut. Ab der zweiten Runde waehlt das Studentenmodell adaptiv weiter, in beiden
 * Gruppen nach derselben Regel.</p>
 */
@ConfigurationProperties(prefix = "tutor")
public class Sitzungseinstellungen {

    /** Hoechstzahl der Aufgaben in der Arbeitsphase. */
    private int aufgaben = 20;

    /** Zeitbudget der Arbeitsphase in Minuten. */
    private int arbeitszeitMinuten = 25;

    /** Versuche je Aufgabe, bevor die Loesung gezeigt wird. */
    private int versuche = 3;

    /** Startwert des Aufgabengenerators, fuer alle Teilnehmenden derselbe. */
    private long seed = 2026L;

    /** Ordner fuer die CSV-Protokolle, relativ zum Projektverzeichnis. */
    private String protokollOrdner = "protokolle";

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
}
