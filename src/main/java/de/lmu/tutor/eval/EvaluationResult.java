package de.lmu.tutor.eval;

import java.util.List;

/**
 * Das Gesamtergebnis einer Auswertung: entweder ein Wert (mit Datentyp) und das
 * Schrittprotokoll -- oder die Kennzeichnung "nicht auswertbar" mit Grund.
 */
public record EvaluationResult(
        boolean auswertbar,
        Value wert,
        String nichtAuswertbarGrund,
        List<EvaluationStep> schritte
) {
    /** Erfolgreiche Auswertung mit Wert und Protokoll. */
    public static EvaluationResult of(Value wert, List<EvaluationStep> schritte) {
        return new EvaluationResult(true, wert, null, List.copyOf(schritte));
    }

    /** Nicht auswertbar -- mit Begruendung und dem bis dahin gesammelten Protokoll. */
    public static EvaluationResult nichtAuswertbar(String grund, List<EvaluationStep> schritte) {
        return new EvaluationResult(false, null, grund, List.copyOf(schritte));
    }

    /** Kurze Ergebniszeile, z. B. "10 : int" oder "nicht auswertbar (...)". */
    public String ergebnisText() {
        return auswertbar ? wert.mitTyp() : "nicht auswertbar (" + nichtAuswertbarGrund + ")";
    }
}