package de.lmu.tutor.buglib;

import java.util.List;

/**
 * Ein Eintrag der Bug Library: eine typische Fehlvorstellung (Misconception).
 *
 * <p>Die Felder entsprechen direkt der Bug-Library-Tabelle aus FQ1 und dem
 * JSON-Schema aus dem Entwurfspapier:</p>
 * <ul>
 *   <li>{@code id}              - eindeutige Kennung, z. B. "B05"</li>
 *   <li>{@code name}            - Kurzbezeichnung</li>
 *   <li>{@code beschreibung}    - was die Fehlvorstellung ausmacht</li>
 *   <li>{@code beispiel}        - ein Beispielausdruck</li>
 *   <li>{@code typischerFehler} - die typische falsche Reaktion</li>
 *   <li>{@code schwierigkeit}   - LEICHT / MITTEL / SCHWER</li>
 *   <li>{@code basisgewicht}    - Basisgewicht b(K) fuer die Aufgabenauswahl</li>
 *   <li>{@code konzept}         - verknuepftes Konzept (spaeter Bruecke zum Evaluator)</li>
 *   <li>{@code feedback}        - geordnete Scaffolding-Stufen (allgemein -> konkret)</li>
 *   <li>{@code untertypen}      - optionale Verfeinerungen</li>
 * </ul>
 */
public record Misconception(
        String id,
        String name,
        String beschreibung,
        String beispiel,
        String typischerFehler,
        Difficulty schwierigkeit,
        int basisgewicht,
        String konzept,
        List<String> feedback,
        List<Subtype> untertypen
) {

    /**
     * Liefert die Feedback-Stufe zum gegebenen Index (0 = allgemeinster Hinweis).
     * Liegt der Index ausserhalb, wird die letzte (konkreteste) Stufe zurueckgegeben.
     * So kann die Diagnose gefahrlos "immer eine Stufe weiter" anfordern.
     */
    public String feedbackStufe(int stufe) {
        if (feedback == null || feedback.isEmpty()) {
            return "";
        }
        int index = Math.max(0, Math.min(stufe, feedback.size() - 1));
        return feedback.get(index);
    }

    /** Anzahl verfuegbarer Feedback-Stufen. */
    public int anzahlFeedbackStufen() {
        return feedback == null ? 0 : feedback.size();
    }

    /** Ob diese Kategorie Untertypen besitzt. */
    public boolean hatUntertypen() {
        return untertypen != null && !untertypen.isEmpty();
    }
}
