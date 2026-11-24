package de.lmu.tutor.web;

import java.util.ArrayList;
import java.util.List;

import de.lmu.tutor.session.Protokolleintrag;

/**
 * Bereitet das Protokoll fuer das Abschlussgespraech auf.
 *
 * <p>Diese Klasse ist bewusst eine eigene Sicht auf die Daten und kein gefiltertes
 * Protokoll. Wuerde die Vorlage die Protokollzeilen direkt bekommen, stuenden dort auch
 * {@code diagnose}, {@code vermutung} und {@code konfidenz} zur Verfuegung, und ein
 * spaeterer Zusatz in der Vorlage koennte sie versehentlich sichtbar machen. Was hier
 * nicht abgebildet wird, kann im Gespraech nicht auf dem Bildschirm landen.</p>
 *
 * <p>Nicht uebernommen werden deshalb: Gruppe, Zielkategorie der Aufgabe, richtige
 * Loesung, Richtigkeit, Diagnose, Vermutung, Konfidenz, Feedbackstufe und die
 * Zaehlerstaende des Studentenmodells.</p>
 */
public final class Gespraechsansicht {

    /** Eine Aufgabe mit allen Eingaben, die dazu gemacht wurden. */
    public record Aufgabenblock(int nummer, String ausdruck, String belegung,
                                List<Eingabe> eingaben) {

        /** Ob zu dieser Aufgabe eine Belegung angezeigt werden muss. */
        public boolean hatBelegung() {
            return belegung != null && !belegung.isBlank();
        }
    }

    /**
     * Eine einzelne Eingabe.
     *
     * @param versuch  die Nummer des Versuchs, 0 bei einer nicht lesbaren Eingabe
     * @param antwort  die Eingabe, so wie sie gemacht wurde
     * @param lesbar   ob sie gewertet wurde
     * @param sekunden wie lange dieser Versuch gedauert hat
     */
    public record Eingabe(int versuch, String antwort, boolean lesbar, long sekunden) {

        /** Die Beschriftung der ersten Spalte. */
        public String marke() {
            return lesbar ? String.valueOf(versuch) : "nicht lesbar";
        }
    }

    /**
     * Gruppiert die Protokollzeilen nach Aufgabe, in der Reihenfolge der Bearbeitung.
     *
     * <p>Die Reihenfolge bleibt, wie sie im Protokoll steht. Sortiert wird nicht, denn im
     * Gespraech wird der Verlauf nachvollzogen, und der ist zeitlich.</p>
     */
    public static List<Aufgabenblock> ausProtokoll(List<Protokolleintrag> protokoll) {
        List<Aufgabenblock> bloecke = new ArrayList<>();
        for (Protokolleintrag zeile : protokoll) {
            Aufgabenblock letzter = bloecke.isEmpty() ? null : bloecke.get(bloecke.size() - 1);
            if (letzter == null || letzter.nummer() != zeile.aufgabenNummer()) {
                letzter = new Aufgabenblock(zeile.aufgabenNummer(), zeile.ausdruck(),
                        zeile.belegung(), new ArrayList<>());
                bloecke.add(letzter);
            }
            letzter.eingaben().add(new Eingabe(zeile.versuch(), zeile.antwort(),
                    zeile.lesbar(), zeile.dauerVersuchSekunden()));
        }
        return bloecke;
    }

    private Gespraechsansicht() {
    }
}
