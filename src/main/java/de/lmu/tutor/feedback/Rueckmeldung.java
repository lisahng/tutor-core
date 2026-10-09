package de.lmu.tutor.feedback;

import java.util.Optional;

/**
 * Die an den Lernenden ausgegebene Rueckmeldung: der Text, die zugrunde liegende
 * Bug-Kategorie und die verwendete Scaffolding-Stufe.
 *
 * <p>Die Kategorie ist nur bei einer begruendeten Diagnose gesetzt. Konnte keine Fehlregel
 * den eingegebenen Wert erklaeren, arbeitet das Feedback mit dem Abgleich des
 * Loesungswegs und nennt bewusst keine Fehlvorstellung. Die Stufe wird trotzdem gefuehrt,
 * denn auch dieser Abgleich ist gestuft und gehoert als abgerufene Feedback-Stufe ins
 * Systemprotokoll.</p>
 *
 * <p>{@code stufe} ist -1, wenn es keine Stufung gibt, also bei einer richtigen
 * Antwort.</p>
 */
public record Rueckmeldung(boolean korrekt, String text, Optional<String> kategorieId, int stufe) {

    /** Bestaetigung bei richtiger Antwort. */
    public static Rueckmeldung korrekt(String text) {
        return new Rueckmeldung(true, text, Optional.empty(), -1);
    }

    /** Hinweis zu einer begruendet diagnostizierten Fehlvorstellung. */
    public static Rueckmeldung fehler(String text, String kategorieId, int stufe) {
        return new Rueckmeldung(false, text, Optional.of(kategorieId), stufe);
    }

    /**
     * Gestufter Abgleich des Loesungswegs, ohne eine Kategorie zu behaupten. Fuer Fehler,
     * die keine Fehlregel erklaeren kann.
     */
    public static Rueckmeldung modellabgleich(String text, int stufe) {
        return new Rueckmeldung(false, text, Optional.empty(), stufe);
    }

    /**
     * Rueckmeldung der Kontrollbedingung: nur richtig oder falsch, ohne Diagnose.
     *
     * <p>Traegt keine Kategorie, weil keine genannt wird. Die Stufe wird trotzdem
     * gefuehrt, damit sich die abgerufenen Feedback-Stufen zwischen den Gruppen
     * vergleichen lassen.</p>
     */
    public static Rueckmeldung ohneDiagnose(String text, int stufe) {
        return new Rueckmeldung(false, text, Optional.empty(), stufe);
    }

    /** Wie {@link #modellabgleich(String, int)}, ohne Stufung. */
    public static Rueckmeldung unbekannterFehler(String text) {
        return new Rueckmeldung(false, text, Optional.empty(), -1);
    }

    /** Ob die Rueckmeldung eine Fehlvorstellung benennt. */
    public boolean nenntKategorie() {
        return kategorieId().isPresent();
    }
}