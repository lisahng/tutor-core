package de.lmu.tutor.session;

/**
 * Wer gerade uebt und in welcher Bedingung.
 *
 * <p>Beides wandert in jede Zeile des Systemprotokolls. Ohne die Kennung liessen sich die
 * Zeilen mehrerer Teilnehmender nicht trennen, ohne die Gruppe nicht vergleichen.</p>
 *
 * <p><b>Zur Kennung.</b> Sie ist ein Pseudonym wie {@code P07} und enthaelt keinen Namen.
 * Die Zuordnung zu einer Person wird getrennt vom Protokoll aufbewahrt, so dass die
 * Logdaten fuer sich genommen niemanden identifizieren.</p>
 *
 * <p><b>Die Gruppe steht im Pseudonym.</b> Sie wird aus der Nummer abgeleitet und nicht
 * mehr von Hand gewaehlt, siehe {@link #ausKennung(String)}. Damit kann die
 * Versuchsleitung die Bedingung im Termin weder vergessen noch verwechseln, und die
 * Gruppen bleiben zwangslaeufig gleich gross. Angezeigt wird die Gruppe nirgends, denn wer
 * liest, dass er in der Kontrollbedingung sitzt, strengt sich moeglicherweise anders an
 * (Cook und Campbell 1979).</p>
 *
 * @param id      das Pseudonym, etwa "P07"
 * @param gruppe  die Versuchsbedingung
 */
public record Teilnehmer(String id, Gruppe gruppe) {

    /**
     * Die beiden Bedingungen der Evaluationsstudie.
     *
     * <p>Beide Gruppen bekommen dieselbe Rueckmeldung, dieselbe Uebungszeit, dieselbe Zahl
     * moeglicher Versuche und dieselbe Diagnose im Hintergrund. Sie unterscheiden sich in
     * genau einer Sache, naemlich darin, wie die naechste Aufgabe ausgewaehlt wird. Nur
     * dann laesst sich ein Unterschied im Ergebnis auf die adaptive Auswahl
     * zurueckfuehren.</p>
     */
    public enum Gruppe {

        /**
         * Volles System: Die naechste Aufgabe waehlt das Studentenmodell nach PFA, also die
         * Kategorie mit der niedrigsten geschaetzten Erfolgswahrscheinlichkeit. Wer eine
         * Fehlvorstellung hat, bekommt dazu mehr Aufgaben.
         */
        TUTOR,

        /**
         * Kontrollbedingung: feste Reihenfolge, reihum durch alle Kategorien, ohne
         * Anpassung an die Leistung.
         *
         * <p>Das entspricht einem gewoehnlichen Aufgabenblatt, auf dem die Aufgaben in
         * einer vorher festgelegten Reihenfolge stehen. Die Gruppe arbeitet also nicht mit
         * einem kuenstlich verschlechterten Werkzeug, sondern mit dem ueblichen. Sie
         * bekommt dieselben Erklaerungen zu ihren Fehlern, nur keine auf sie zugeschnittene
         * Aufgabenauswahl.</p>
         */
        KONTROLLE
    }

    public Teilnehmer {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Die Teilnehmerkennung darf nicht leer sein");
        }
        if (gruppe == null) {
            throw new IllegalArgumentException("Die Gruppe darf nicht fehlen");
        }
    }

    /**
     * Baut den Teilnehmer aus dem Pseudonym und leitet die Gruppe aus dessen Nummer ab.
     *
     * <p><b>Die Regel.</b> Ungerade Nummer bedeutet Tutorgruppe, gerade Nummer
     * Kontrollgruppe. P01 ist also adaptiv, P02 nicht, P03 wieder adaptiv. Massgeblich ist
     * die letzte Ziffernfolge der Kennung, Buchstaben davor spielen keine Rolle.</p>
     *
     * <p><b>Warum so.</b> Eine Zuteilung im Wechsel garantiert bei kleiner Stichprobe
     * gleich grosse Gruppen, waehrend echtes Losen bei zwanzig Personen leicht elf zu neun
     * ergibt. Sie ist allerdings vorhersehbar, deshalb muss die Zuordnung von Pseudonym zu
     * Person <em>vor</em> den Terminen feststehen und in der Reihenfolge der Anmeldung
     * vergeben werden. Wer sich die Pseudonyme im Termin aussucht, entscheidet damit ueber
     * die Gruppe und hebt den Vorteil wieder auf.</p>
     *
     * @param kennung das Pseudonym, etwa "P07"
     * @throws IllegalArgumentException wenn die Kennung keine Ziffer enthaelt
     */
    public static Teilnehmer ausKennung(String kennung) {
        return new Teilnehmer(kennung == null ? null : kennung.trim(),
                gruppeZu(nummerAus(kennung)));
    }

    /**
     * Die Nummer einer Kennung, also deren letzte zusammenhaengende Ziffernfolge.
     *
     * @throws IllegalArgumentException wenn keine Ziffer vorkommt
     */
    public static int nummerAus(String kennung) {
        if (kennung == null) {
            throw new IllegalArgumentException("Die Teilnehmerkennung darf nicht leer sein");
        }
        String text = kennung.trim();
        int ende = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            if (Character.isDigit(text.charAt(i))) {
                ende = i;
                break;
            }
        }
        if (ende < 0) {
            throw new IllegalArgumentException(
                    "Das Pseudonym braucht eine Nummer, etwa P07, denn aus ihr ergibt sich "
                            + "die Gruppe. Angegeben war: " + kennung);
        }
        int anfang = ende;
        while (anfang > 0 && Character.isDigit(text.charAt(anfang - 1))) {
            anfang--;
        }
        // Fuehrende Nullen sind erlaubt, und die Laenge ist begrenzt, damit eine
        // versehentlich sehr lange Ziffernfolge keinen Ueberlauf ausloest.
        String ziffern = text.substring(anfang, ende + 1);
        if (ziffern.length() > 9) {
            ziffern = ziffern.substring(ziffern.length() - 9);
        }
        return Integer.parseInt(ziffern);
    }

    /** Ungerade Nummer bedeutet Tutorgruppe, gerade Kontrollgruppe. */
    public static Gruppe gruppeZu(int nummer) {
        return nummer % 2 == 0 ? Gruppe.KONTROLLE : Gruppe.TUTOR;
    }

    /**
     * Platzhalter fuer Laeufe ausserhalb der Studie, etwa Demos und Tests.
     *
     * <p>Die Gruppe ist dabei {@code TUTOR}, weil das dem vollen System entspricht.</p>
     */
    public static Teilnehmer anonym() {
        return new Teilnehmer("anonym", Gruppe.TUTOR);
    }

    /** Ob diese Person in der Kontrollbedingung uebt, also ohne adaptive Auswahl. */
    public boolean istKontrollgruppe() {
        return gruppe == Gruppe.KONTROLLE;
    }
}
