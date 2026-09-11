package de.lmu.tutor.diagnose;

import java.util.Optional;

import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;

/**
 * Ergebnis des Fehlerklassifikators: entweder "korrekt", oder eine (moeglichst
 * konkrete) diagnostizierte Fehlvorstellung samt Begruendung und Konfidenz-Stufe.
 *
 * <p>{@code Konfidenz} spiegelt wider, wie sicher die Diagnose ist - siehe
 * {@link Fehlerklassifikator} fuer die Herleitung:</p>
 * <ul>
 *   <li>{@code EXAKT} - die Antwort passt zu einer eindeutig erkennbaren Signatur
 *       eines Fehlertyps (z. B. Wert korrekt, Typ falsch -> B07).</li>
 *   <li>{@code SIMULIERT} - eine der bekannten Fehlregeln wurde auf den Ausdruck
 *       angewendet ("Perturbationsmodell", Brown &amp; VanLehn 1980) und hat genau
 *       den abgegebenen Wert reproduziert.</li>
 *   <li>{@code ZIELKATEGORIE} - keine Fehlregel passt exakt, aber die Aufgabe wurde
 *       gezielt fuer diese Kategorie generiert; sie ist die plausibelste Vermutung.</li>
 *   <li>{@code VERMUTET} - grober struktureller Treffer (z. B. anhand des
 *       Wurzeloperators), ohne dass ein konkreter Fehlwert reproduziert werden konnte.</li>
 * </ul>
 */
public record Diagnose(
        boolean korrekt,
        Optional<Misconception> misconception,
        Optional<Subtype> subtype,
        Konfidenz konfidenz,
        String begruendung
) {
    public enum Konfidenz { KEINE, EXAKT, SIMULIERT, ZIELKATEGORIE, VERMUTET, UNBEKANNT }

    /**
     * Fabrikmethode fuer eine richtige Antwort.
     *
     * <p>Sie heisst bewusst nicht {@code korrekt()}: Die Record-Komponente
     * {@code korrekt} erzeugt bereits einen Accessor dieses Namens, und ein
     * zweites parameterloses {@code korrekt()} waere ein Konflikt mit ihm.</p>
     */
    public static Diagnose korrekteAntwort() {
        return new Diagnose(true, Optional.empty(), Optional.empty(), Konfidenz.KEINE, "Antwort korrekt.");
    }

    public static Diagnose von(Misconception m, Konfidenz konfidenz, String begruendung) {
        return new Diagnose(false, Optional.of(m), Optional.empty(), konfidenz, begruendung);
    }

    public static Diagnose vonMitUntertyp(Misconception m, Subtype s, Konfidenz konfidenz, String begruendung) {
        return new Diagnose(false, Optional.of(m), Optional.ofNullable(s), konfidenz, begruendung);
    }

    /** Keine Fehlregel und keine Zielkategorie passen - Kandidat fuer die Kommentarspalte (vgl. Besprechung 1). */
    public static Diagnose unbekannt(String begruendung) {
        return new Diagnose(false, Optional.empty(), Optional.empty(), Konfidenz.UNBEKANNT, begruendung);
    }
}