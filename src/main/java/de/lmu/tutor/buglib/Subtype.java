package de.lmu.tutor.buglib;

/**
 * Ein Untertyp einer Fehlerkategorie (z. B. B05a "Array-Index ausserhalb der Grenzen").
 */
public record Subtype(String id, String name, String beispiel) {
}
