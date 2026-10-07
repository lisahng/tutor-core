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
 * @param id      das Pseudonym, etwa "P07"
 * @param gruppe  die Versuchsbedingung
 */
public record Teilnehmer(String id, Gruppe gruppe) {

    /**
     * Die beiden Bedingungen der Evaluationsstudie.
     *
     * <p>Beide Gruppen bekommen dieselben generierten Aufgaben, dieselbe Uebungszeit und
     * dieselbe Zahl moeglicher Versuche. Sie unterscheiden sich in genau einer Sache,
     * naemlich der Rueckmeldung. Nur dann laesst sich ein Unterschied im Ergebnis auf das
     * diagnostische Feedback zurueckfuehren.</p>
     */
    public enum Gruppe {

        /** Volles System: Diagnose und gestuftes Feedback. */
        TUTOR,

        /**
         * Kontrollbedingung: nur richtig oder falsch, ohne Diagnose und ohne Stufen.
         *
         * <p>Das entspricht der gewohnten Situation, denn genau diese Auskunft gibt auch
         * ein Compiler. Die Gruppe arbeitet also nicht mit einem kuenstlich
         * verschlechterten Werkzeug.</p>
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
     * Platzhalter fuer Laeufe ausserhalb der Studie, etwa Demos und Tests.
     *
     * <p>Die Gruppe ist dabei {@code TUTOR}, weil das dem vollen System entspricht.</p>
     */
    public static Teilnehmer anonym() {
        return new Teilnehmer("anonym", Gruppe.TUTOR);
    }

    /** Ob diese Person in der Kontrollbedingung uebt. */
    public boolean istKontrollgruppe() {
        return gruppe == Gruppe.KONTROLLE;
    }
}