package de.lmu.tutor.student;

/**
 * Die beiden Lernraten der Performance Factors Analysis (Pavlik et al. 2009).
 *
 * <p>Das Modell schaetzt fuer jede Kategorie, wie wahrscheinlich die naechste Aufgabe
 * richtig geloest wird:</p>
 *
 * <pre>
 *   m = beta + gamma * erfolge + rho * fehler
 *   P(richtig) = 1 / (1 + e^(-m))
 * </pre>
 *
 * <p>Dabei ist {@code beta} die Leichtigkeit der Kategorie (steht in der Bug Library),
 * {@code gamma} das Gewicht jedes bisherigen Erfolgs und {@code rho} das Gewicht jedes
 * bisherigen Fehlers.</p>
 *
 * <p><b>Zu den Vorzeichen:</b> {@code gamma} ist positiv, jeder Erfolg hebt die
 * Wahrscheinlichkeit. {@code rho} ist hier negativ, jeder Fehler senkt sie. Das ist eine
 * bewusste Vereinfachung: In der Originalformulierung kann rho auch positiv sein, weil
 * ein Fehler samt Rueckmeldung ebenfalls zum Lernen beitraegt. Fuer die Aufgabenauswahl
 * waere das aber kontraproduktiv, denn dann wuerde wiederholtes Scheitern die Kategorie
 * als beherrscht erscheinen lassen. {@code gamma} ist ausserdem betragsmaessig groesser
 * als {@code rho}, ein Erfolg wiegt also schwerer als ein Fehler.</p>
 *
 * <p><b>Wichtig fuer die Arbeit:</b> Die Werte in {@link #STANDARD} sind plausible
 * Startwerte, keine kalibrierten Schaetzungen. Eine echte PFA-Kalibrierung braucht
 * Logdaten aus der Studie. Solange die fehlen, sollte in der Arbeit stehen, dass die
 * Parameter gesetzt und nicht geschaetzt wurden.</p>
 *
 * @param gamma Gewicht jedes bisherigen Erfolgs (positiv)
 * @param rho   Gewicht jedes bisherigen Fehlers (hier negativ)
 */
public record PfaParameter(double gamma, double rho) {

    /** Vorlaeufige Startwerte, bis Daten aus der Studie vorliegen. */
    public static final PfaParameter STANDARD = new PfaParameter(0.4, -0.2);

    public PfaParameter {
        if (gamma <= 0) {
            throw new IllegalArgumentException("gamma muss positiv sein, war: " + gamma);
        }
    }
}