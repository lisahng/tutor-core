package de.lmu.tutor.buglib;

/**
 * Schwierigkeitsgrad einer Aufgabe bzw. Fehlerkategorie.
 * Die Stufe (1..3) laesst sich spaeter fuer die Aufgabenauswahl nutzen.
 */
public enum Difficulty {
    LEICHT(1),
    MITTEL(2),
    SCHWER(3);

    private final int stufe;

    Difficulty(int stufe) {
        this.stufe = stufe;
    }

    /** Numerische Stufe: LEICHT = 1, MITTEL = 2, SCHWER = 3. */
    public int stufe() {
        return stufe;
    }

    /** Wandelt einen Text wie "MITTEL" (Gross-/Kleinschreibung egal) in den Enum-Wert um. */
    public static Difficulty vonText(String text) {
        return Difficulty.valueOf(text.trim().toUpperCase());
    }
}
