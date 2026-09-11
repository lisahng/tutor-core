package de.lmu.tutor.feedback;

import java.util.Optional;

/**
 * Die an den Lernenden ausgegebene Rueckmeldung: der Text, die zugrunde liegende
 * Bug-Kategorie (falls vorhanden) und die verwendete Scaffolding-Stufe (0 = allgemeinster
 * Hinweis). {@code stufe} ist nur bei einer Fehlermeldung mit bekannter Kategorie
 * aussagekraeftig, sonst -1.
 */
public record Rueckmeldung(boolean korrekt, String text, Optional<String> kategorieId, int stufe) {

    public static Rueckmeldung korrekt(String text) {
        return new Rueckmeldung(true, text, Optional.empty(), -1);
    }

    public static Rueckmeldung fehler(String text, String kategorieId, int stufe) {
        return new Rueckmeldung(false, text, Optional.of(kategorieId), stufe);
    }

    public static Rueckmeldung unbekannterFehler(String text) {
        return new Rueckmeldung(false, text, Optional.empty(), -1);
    }
}