package de.lmu.tutor.buglib;

import java.util.List;

/**
 * Ein Eintrag der Bug Library: eine typische Fehlvorstellung (Misconception).
 *
 * <p>Felder:</p>
 * <ul>
 *   <li>{@code id}, {@code name}, {@code beschreibung}, {@code beispiel},
 *       {@code typischerFehler}, {@code konzept} - beschreibende Angaben</li>
 *   <li>{@code beta} - PFA-Leichtigkeit (easiness) der Kategorie, aus Daten
 *       kalibriert (Pavlik et al. 2009). Aus beta ergibt sich alles Weitere
 *       (Aufgabenauswahl, ggf. Schwierigkeit) - es wird nichts von Hand gesetzt.</li>
 *   <li>{@code feedback} - geordnete Scaffolding-Stufen (allgemein -> konkret)</li>
 *   <li>{@code untertypen} - optionale Verfeinerungen</li>
 * </ul>
 */
public record Misconception(
        String id,
        String name,
        String beschreibung,
        String beispiel,
        String typischerFehler,
        double beta,
        String konzept,
        List<String> feedback,
        List<Subtype> untertypen
) {

    /**
     * Liefert die Feedback-Stufe zum gegebenen Index (0 = allgemeinster Hinweis).
     * Liegt der Index ausserhalb, wird die letzte (konkreteste) Stufe zurueckgegeben.
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