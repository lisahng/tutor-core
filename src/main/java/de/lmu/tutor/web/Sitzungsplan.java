package de.lmu.tutor.web;

import java.time.Duration;

/**
 * Der zeitliche Rahmen einer Uebungssitzung.
 *
 * <p>Die Evaluationssitzung dauert insgesamt 45 bis 60 Minuten. Davon entfaellt nur ein Teil
 * auf das Ueben am System, der Rest auf Begruessung, Einverstaendniserklaerung, Vortest,
 * Fragebogen und Interview. Die Arbeitsphase wird deshalb doppelt begrenzt, durch eine
 * Hoechstzahl an Aufgaben und durch eine Restzeit. Was zuerst erreicht ist, beendet sie.</p>
 *
 * <p><b>Warum beides.</b> Eine reine Aufgabenzahl laesst die Dauer offen, und in der
 * ChomskyTrainer-Studie (Schmutz, Blanchette und Strickroth 2026) wurde niemand in der
 * vorgesehenen Zeit fertig. Eine reine Zeitgrenze wiederum liesse schnelle Teilnehmende
 * deutlich mehr Aufgaben bearbeiten als langsame, was die Logdaten schwer vergleichbar
 * macht. Beide Grenzen zusammen halten die Sitzung im Zeitplan und die Datenmenge je
 * Person in einem aehnlichen Rahmen.</p>
 *
 * <p><b>Zu den Standardwerten.</b> Zwanzig Aufgaben bei etwa einer Minute je Aufgabe passen
 * in 25 Minuten. Wer langsamer ist, schafft weniger und wird von der Zeit gestoppt. Die
 * Werte lassen sich in {@code application.properties} ohne Codeaenderung anpassen, was fuer
 * den Pilotlauf wichtig ist: dort zeigt sich, wie lange eine Aufgabe tatsaechlich dauert.</p>
 *
 * @param maximaleAufgaben Hoechstzahl der Aufgaben in der Arbeitsphase
 * @param arbeitszeit      Zeitbudget der Arbeitsphase
 * @param maximaleVersuche Versuche je Aufgabe, bevor die Loesung gezeigt wird
 */
public record Sitzungsplan(int maximaleAufgaben, Duration arbeitszeit, int maximaleVersuche) {

    public Sitzungsplan {
        if (maximaleAufgaben < 1) {
            throw new IllegalArgumentException("Mindestens eine Aufgabe");
        }
        if (arbeitszeit == null || arbeitszeit.isNegative() || arbeitszeit.isZero()) {
            throw new IllegalArgumentException("Die Arbeitszeit muss positiv sein");
        }
        if (maximaleVersuche < 1) {
            throw new IllegalArgumentException("Mindestens ein Versuch");
        }
    }

    /**
     * Zwanzig Aufgaben in 25 Minuten, drei Versuche je Aufgabe.
     *
     * <p>Drei Versuche deshalb, weil die Bug Library drei Feedback-Stufen vorsieht. Nach dem
     * dritten Versuch ist das Scaffolding ausgeschoepft und die Loesung wird gezeigt.</p>
     */
    public static Sitzungsplan standard() {
        return new Sitzungsplan(20, Duration.ofMinutes(25), 3);
    }

    /** Die Arbeitszeit in Sekunden, fuer die Anzeige im Browser. */
    public long arbeitszeitSekunden() {
        return arbeitszeit.toSeconds();
    }
}
