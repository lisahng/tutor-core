package de.lmu.tutor.diagnose;

import java.util.Optional;

import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;

/**
 * Ergebnis des Fehlerklassifikators: entweder korrekt, oder eine diagnostizierte
 * Fehlvorstellung samt Begruendung und Konfidenzstufe.
 *
 * <p>Die Konfidenz sagt, wie gut das System seine eigene Diagnose belegen kann:</p>
 *
 * <ul>
 *   <li>{@code EXAKT} - die Antwort hat eine eindeutige Signatur, etwa ein richtiger Wert
 *       bei falschem Datentyp.</li>
 *   <li>{@code SIMULIERT} - eine bekannte Fehlregel wurde auf den Ausdruck angewendet und
 *       hat genau den abgegebenen Wert reproduziert (Perturbationsmodell nach Brown und
 *       VanLehn 1980).</li>
 *   <li>{@code ZIELKATEGORIE} - keine Regel erklaert den Wert. Es bleibt die Kategorie,
 *       fuer die die Aufgabe erzeugt wurde.</li>
 *   <li>{@code VERMUTET} - grober Treffer anhand der Ausdrucksstruktur.</li>
 *   <li>{@code UNBEKANNT} - nichts passt.</li>
 * </ul>
 *
 * <p>Nur die ersten beiden Stufen sind erklaerte Diagnosen, siehe {@link #erklaert()}.
 * Die uebrigen sind Vermutungen und werden im Systemprotokoll gesondert gefuehrt. Der
 * Grund: Aufgaben einer Kategorie provozieren meist Fehler eben dieser Kategorie. Wuerde
 * man die Zielkategorie als Diagnose zaehlen, traefe das System oft zufaellig richtig,
 * ohne den Fehler erklaert zu haben, und die Trefferquote in Strang 3 der Studie waere
 * geschoent.</p>
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
     * Ob die Diagnose begruendet ist, also auf einer eindeutigen Signatur oder einer
     * nachgerechneten Fehlregel beruht. Nur solche Diagnosen zaehlen in der strengen
     * Trefferquote.
     */
    public boolean erklaert() {
        return konfidenz == Konfidenz.EXAKT || konfidenz == Konfidenz.SIMULIERT;
    }

    /**
     * Ob eine Kategorie vermutet, aber nicht begruendet wurde. Diese Faelle zeigen
     * Luecken in der Bug Library und werden gesondert ausgewertet.
     */
    public boolean nurVermutet() {
        return !korrekt && !erklaert();
    }

    /**
     * Fabrikmethode fuer eine richtige Antwort.
     *
     * <p>Sie heisst bewusst nicht {@code korrekt()}: Die Record-Komponente erzeugt
     * bereits einen Accessor dieses Namens.</p>
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

    /** Keine Fehlregel und keine Zielkategorie passen. */
    public static Diagnose unbekannt(String begruendung) {
        return new Diagnose(false, Optional.empty(), Optional.empty(), Konfidenz.UNBEKANNT, begruendung);
    }
}