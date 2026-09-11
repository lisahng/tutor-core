package de.lmu.tutor.diagnose;

import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.Value;

/**
 * Vergleichsmodul (Schritt 5, erster Teil): gleicht die {@link NutzerAntwort} mit der
 * vorab vom {@code StepEvaluator} berechneten {@link EvaluationResult} ab.
 *
 * <p>Da das System den Ausdruck selbst generiert und die Loesung vorab berechnet
 * (3.2.2), ist der Vergleich ein direkter Abgleich von Wert, Datentyp und
 * Auswertbarkeit - kein Parsen freier Nutzereingaben.</p>
 */
public final class Vergleichsmodul {

    public Vergleichsergebnis vergleiche(EvaluationResult referenz, NutzerAntwort antwort) {
        boolean auswertbarkeitKorrekt = referenz.auswertbar() == antwort.auswertbar();

        if (!referenz.auswertbar() || !antwort.auswertbar()) {
            // Mindestens eine Seite haelt den Ausdruck fuer nicht auswertbar:
            // Wert/Typ sind dann nicht sinnvoll vergleichbar. Stimmen beide in der
            // Einschaetzung ueberein, gilt das als vollstaendig korrekt.
            return new Vergleichsergebnis(auswertbarkeitKorrekt, auswertbarkeitKorrekt, auswertbarkeitKorrekt);
        }

        Value referenzWert = referenz.wert();
        Value nutzerWert = antwort.wert();
        boolean typKorrekt = nutzerWert != null && referenzWert.typ() == nutzerWert.typ();
        boolean wertKorrekt = nutzerWert != null && werteGleich(referenzWert, nutzerWert);
        return new Vergleichsergebnis(true, wertKorrekt, typKorrekt);
    }

    /**
     * Numerische Werte werden auf Gleichheit verglichen (double: mit kleiner Toleranz,
     * damit int-Werte auch mit double-Werten verglichen werden koennen - relevant fuer
     * den Fehlerklassifikator, der so z. B. B07 erkennt), sonst per equals(). Statisch
     * und oeffentlich, damit auch der {@link Fehlerklassifikator} simulierte Werte
     * gegen die Nutzerantwort abgleichen kann.
     */
    public static boolean werteGleich(Value referenz, Value nutzer) {
        if (referenz.typ().isNumeric() && nutzer.typ().isNumeric()) {
            return Math.abs(referenz.alsZahl() - nutzer.alsZahl()) < 1e-9;
        }
        if (referenz.typ() != nutzer.typ()) {
            return false;
        }
        return referenz.wert().equals(nutzer.wert());
    }
}