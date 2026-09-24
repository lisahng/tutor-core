package de.lmu.tutor.student;

/**
 * Der Kenntnisstand einer Person in einer Bug-Kategorie: wie oft sie dort richtig und
 * wie oft sie falsch lag, dazu die aktuelle PFA-Schaetzung.
 *
 * <p>Nur ein Lesezugriff nach aussen. Das {@link Studentenmodell} fuehrt die Zaehler
 * selbst, gibt hier aber eine unveraenderliche Momentaufnahme heraus, damit niemand
 * versehentlich an den internen Daten dreht. Fuer das Systemprotokoll der
 * Evaluationsstudie ist das die passende Form.</p>
 *
 * @param kategorieId              die Bug-Kategorie, z. B. "B01"
 * @param erfolge                  Anzahl richtiger Antworten in dieser Kategorie
 * @param fehler                   Anzahl falscher Antworten in dieser Kategorie
 * @param erfolgswahrscheinlichkeit PFA-Schaetzung fuer die naechste Aufgabe (0 bis 1)
 */
public record Kategoriestatistik(
        String kategorieId,
        int erfolge,
        int fehler,
        double erfolgswahrscheinlichkeit
) {

    /** Gesamtzahl der Versuche in dieser Kategorie. */
    public int versuche() {
        return erfolge + fehler;
    }

    /** Anteil der Fehler an allen Versuchen; ohne Versuche 0. */
    public double fehlerquote() {
        return versuche() == 0 ? 0.0 : (double) fehler / versuche();
    }

    @Override
    public String toString() {
        return String.format("%s: %d richtig, %d falsch, P=%.3f",
                kategorieId, erfolge, fehler, erfolgswahrscheinlichkeit);
    }
}