package de.lmu.tutor.diagnose;

/**
 * Ergebnis des Vergleichsmoduls: gleicht die {@link NutzerAntwort} feldweise
 * mit der vorab berechneten Referenzloesung ab.
 *
 * <p>Die drei Teilvergleiche sind bewusst getrennt, weil sie unterschiedliche
 * Fehler anzeigen: {@code auswertbarkeitKorrekt = false} deutet auf B08 hin
 * (nicht-auswertbaren Ausdruck nicht erkannt bzw. umgekehrt), waehrend
 * {@code wertKorrekt = true, typKorrekt = false} typischerweise B07 ist
 * (Wert stimmt, Datentyp nicht).</p>
 */
public record Vergleichsergebnis(
        boolean auswertbarkeitKorrekt,
        boolean wertKorrekt,
        boolean typKorrekt
) {
    /** Insgesamt korrekt, wenn alle Teilvergleiche stimmen (bei "nicht auswertbar" reicht die Einschaetzung selbst). */
    public boolean korrekt() {
        if (!auswertbarkeitKorrekt) {
            return false;
        }
        // Wenn beide Seiten "nicht auswertbar" sagen, gibt es keinen Wert/Typ zu vergleichen.
        return wertKorrekt && typKorrekt;
    }
}