package de.lmu.tutor.diagnose;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;

/**
 * Ergebnis des Fehlerklassifikators: entweder korrekt, oder eine oder mehrere
 * diagnostizierte Fehlvorstellungen samt Begruendung und Konfidenzstufe.
 *
 * <p>Die Konfidenz sagt, wie gut das System seine eigene Diagnose belegen kann:</p>
 *
 * <ul>
 *   <li>{@code EXAKT} - die Antwort hat eine eindeutige Signatur.</li>
 *   <li>{@code SIMULIERT} - eine Fehlregel hat den abgegebenen Wert reproduziert.</li>
 *   <li>{@code ZIELKATEGORIE} - keine Regel erklaert den Wert, es bleibt die Kategorie,
 *       fuer die die Aufgabe erzeugt wurde.</li>
 *   <li>{@code VERMUTET} - grober Treffer anhand der Ausdrucksstruktur.</li>
 *   <li>{@code UNBEKANNT} - nichts passt.</li>
 * </ul>
 *
 * <p>Nur die ersten beiden sind erklaerte Diagnosen, siehe {@link #erklaert()}. Die
 * uebrigen werden im Systemprotokoll gesondert als Vermutung gefuehrt, damit die
 * Trefferquote nicht durch Zufallstreffer geschoent wird.</p>
 *
 * <p><b>Kombinationen.</b> {@code kategorien} enthaelt in der Regel genau einen Eintrag.
 * Laesst sich der Wert nur dadurch erklaeren, dass zwei Fehlvorstellungen zusammenkommen,
 * stehen dort mehrere. Die erste gilt als fuehrend und wird fuer die Fehlerhistorie
 * verwendet, das Feedback spricht beide an.</p>
 *
 * @param korrekt     ob die Antwort richtig war
 * @param kategorien  die diagnostizierten Fehlvorstellungen, leer wenn keine erkannt wurde
 * @param subtype     ein Untertyp der fuehrenden Kategorie, falls zutreffend
 * @param konfidenz   wie die Diagnose zustande kam
 * @param begruendung woran die Lernende vermutlich gedacht hat
 */
public record Diagnose(
        boolean korrekt,
        List<Misconception> kategorien,
        Optional<Subtype> subtype,
        Konfidenz konfidenz,
        String begruendung
) {
    public enum Konfidenz { KEINE, EXAKT, SIMULIERT, ZIELKATEGORIE, VERMUTET, UNBEKANNT }

    public Diagnose {
        kategorien = List.copyOf(kategorien);
    }

    /** Die fuehrende Kategorie, unter der die Fehlerhistorie gefuehrt wird. */
    public Optional<Misconception> misconception() {
        return kategorien.isEmpty() ? Optional.empty() : Optional.of(kategorien.get(0));
    }

    /** Ob hier mehrere Fehlvorstellungen zusammenkommen. */
    public boolean istKombination() {
        return kategorien.size() > 1;
    }

    /** Die IDs aller beteiligten Kategorien, bei einer Kombination mit Pluszeichen verbunden. */
    public String kategorieIds() {
        return kategorien.stream().map(Misconception::id).collect(Collectors.joining("+"));
    }

    /**
     * Ob die Diagnose begruendet ist, also auf einer eindeutigen Signatur oder einer
     * nachgerechneten Fehlregel beruht. Nur solche zaehlen in der strengen Trefferquote.
     */
    public boolean erklaert() {
        return konfidenz == Konfidenz.EXAKT || konfidenz == Konfidenz.SIMULIERT;
    }

    /** Ob eine Kategorie vermutet, aber nicht begruendet wurde. */
    public boolean nurVermutet() {
        return !korrekt && !erklaert();
    }

    /**
     * Fabrikmethode fuer eine richtige Antwort.
     *
     * <p>Sie heisst bewusst nicht {@code korrekt()}: Die Record-Komponente erzeugt bereits
     * einen Accessor dieses Namens.</p>
     */
    public static Diagnose korrekteAntwort() {
        return new Diagnose(true, List.of(), Optional.empty(), Konfidenz.KEINE, "Antwort korrekt.");
    }

    public static Diagnose von(Misconception m, Konfidenz konfidenz, String begruendung) {
        return new Diagnose(false, List.of(m), Optional.empty(), konfidenz, begruendung);
    }

    /** Mehrere Fehlvorstellungen, die zusammen den Wert erklaeren. */
    public static Diagnose vonKombination(List<Misconception> kategorien, Konfidenz konfidenz,
                                          String begruendung) {
        return new Diagnose(false, kategorien, Optional.empty(), konfidenz, begruendung);
    }

    public static Diagnose vonMitUntertyp(Misconception m, Subtype s, Konfidenz konfidenz,
                                          String begruendung) {
        return new Diagnose(false, List.of(m), Optional.ofNullable(s), konfidenz, begruendung);
    }

    /** Keine Fehlregel und keine Zielkategorie passen. */
    public static Diagnose unbekannt(String begruendung) {
        return new Diagnose(false, List.of(), Optional.empty(), Konfidenz.UNBEKANNT, begruendung);
    }
}