package de.lmu.tutor.diagnose;

import de.lmu.tutor.eval.Value;

/**
 * Die Antwort, die der Lernende zu einer Aufgabe abgibt: entweder ein Wert
 * (mit Datentyp) oder die Einschaetzung, der Ausdruck sei nicht auswertbar.
 *
 * <p>Das System generiert die Aufgaben selbst und kennt die Referenzloesung
 * vorab (vgl. 3.2.2 der Zulassungsarbeit) - es wird also kein Parser fuer
 * freien Nutzer-Text benoetigt. {@code NutzerAntwort} bildet genau das ab,
 * was die Aufgabenblaetter abfragen: Wert und Datentyp, oder ein Kreuz bei
 * "nicht auswertbar".</p>
 */
public record NutzerAntwort(boolean auswertbar, Value wert) {

    /** Der Lernende gibt einen Wert (mit Datentyp) an. */
    public static NutzerAntwort wert(Value wert) {
        return new NutzerAntwort(true, wert);
    }

    /** Der Lernende kreuzt "nicht auswertbar" an. */
    public static NutzerAntwort nichtAuswertbar() {
        return new NutzerAntwort(false, null);
    }
}