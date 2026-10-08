package de.lmu.tutor.web;

import java.util.Optional;

import de.lmu.tutor.ast.JType;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;

/**
 * Uebersetzt die Eingabe aus dem Browser in eine {@link NutzerAntwort}.
 *
 * <p>Im Browser tippen Lernende Text. Die Diagnose arbeitet dagegen mit typisierten Werten,
 * denn der Unterschied zwischen {@code 4} und {@code 4.0} ist genau das, was die
 * Fehlvorstellung zur Integer-Division sichtbar macht. Diese Klasse bildet die eine Welt auf
 * die andere ab und ist dabei bewusst streng: Sie raet nicht.</p>
 *
 * <p><b>Warum eine unleserliche Eingabe kein Fehlversuch ist.</b> Wer sich vertippt, hat
 * keine Fehlvorstellung gezeigt. Wuerde ein Tippfehler als falsche Antwort zaehlen, liefe
 * das Scaffolding eine Stufe weiter und die Fehlerhistorie des Studentenmodells bekaeme
 * einen Eintrag, der nichts ueber den Kenntnisstand aussagt. Der Parser liefert in diesem
 * Fall {@link Optional#empty()}, und die Oberflaeche bittet um eine neue Eingabe, ohne den
 * Versuch zu zaehlen.</p>
 *
 * <p><b>Die Konventionen.</b> Sie stehen auch in der Oberflaeche ueber dem Eingabefeld, denn
 * ungeschriebene Regeln waeren eine unfaire Huerde:</p>
 * <ul>
 *   <li>Ganze Zahlen ohne Punkt sind {@code int}, mit Punkt {@code double}</li>
 *   <li>Zeichen in einfachen, Zeichenketten in doppelten Anfuehrungszeichen</li>
 *   <li>{@code true} und {@code false} fuer {@code boolean}</li>
 *   <li>"nicht auswertbar" fuer Ausdruecke, die zur Laufzeit scheitern</li>
 * </ul>
 *
 * <p><b>Zur Rolle des erwarteten Typs.</b> Ist die Musterloesung eine Zeichenkette, wird eine
 * Eingabe ohne Anfuehrungszeichen als Zeichenkette gelesen, sofern sie sich nicht als Zahl,
 * Wahrheitswert oder Zeichen lesen laesst. Damit scheitert niemand daran, die
 * Anfuehrungszeichen vergessen zu haben. Verraten wird dadurch nichts: Wer {@code 7} tippt,
 * bekommt weiterhin ein {@code int} und damit die Rueckmeldung zum Typfehler.</p>
 */
public final class Antwortparser {

    /** Eingaben, die als "nicht auswertbar" gelten, klein geschrieben. */
    private static final String[] NICHT_AUSWERTBAR = {
            "nicht auswertbar", "nichtauswertbar", "fehler", "exception", "laufzeitfehler",
            "error", "-"
    };

    private Antwortparser() {
    }

    /**
     * Liest die Eingabe als Java-Wert.
     *
     * @param eingabe       der getippte Text, darf null oder leer sein
     * @param erwarteterTyp der Typ der Musterloesung, fuer den Rueckfall auf Zeichenketten;
     *                      {@link Optional#empty()}, wenn der Ausdruck nicht auswertbar ist
     * @return die Antwort, oder leer, wenn die Eingabe nicht lesbar war
     */
    public static Optional<NutzerAntwort> lies(String eingabe, Optional<JType> erwarteterTyp) {
        if (eingabe == null) {
            return Optional.empty();
        }
        String text = eingabe.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        for (String marker : NICHT_AUSWERTBAR) {
            if (text.equalsIgnoreCase(marker)) {
                return Optional.of(NutzerAntwort.nichtAuswertbar());
            }
        }

        // Anfuehrungszeichen sind eindeutig und haben Vorrang vor allem anderen.
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return Optional.of(NutzerAntwort.wert(Value.ofString(text.substring(1, text.length() - 1))));
        }
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            String inhalt = text.substring(1, text.length() - 1);
            return inhalt.length() == 1
                    ? Optional.of(NutzerAntwort.wert(Value.ofChar(inhalt.charAt(0))))
                    : Optional.empty();
        }

        if (text.equalsIgnoreCase("true")) {
            return Optional.of(NutzerAntwort.wert(Value.ofBool(true)));
        }
        if (text.equalsIgnoreCase("false")) {
            return Optional.of(NutzerAntwort.wert(Value.ofBool(false)));
        }

        Optional<NutzerAntwort> zahl = alsZahl(text);
        if (zahl.isPresent()) {
            return zahl;
        }

        // Letzter Rueckfall: eine Zeichenkette ohne Anfuehrungszeichen, aber nur dann, wenn
        // auch die Musterloesung eine Zeichenkette ist.
        if (erwarteterTyp.isPresent() && erwarteterTyp.get() == JType.STRING) {
            return Optional.of(NutzerAntwort.wert(Value.ofString(text)));
        }
        return Optional.empty();
    }

    /**
     * Liest eine Zahl.
     *
     * <p>Ein Dezimalkomma wird als Dezimalpunkt gelesen. Das ist eine Zugestaendnis an die
     * deutsche Tastaturgewohnheit und kein Verraten: Der Typ bleibt {@code double}, egal
     * welches Zeichen getippt wurde.</p>
     */
    private static Optional<NutzerAntwort> alsZahl(String text) {
        String normiert = text.replace(',', '.');
        boolean hatPunkt = normiert.contains(".");
        try {
            if (hatPunkt) {
                return Optional.of(NutzerAntwort.wert(Value.ofDouble(Double.parseDouble(normiert))));
            }
            return Optional.of(NutzerAntwort.wert(Value.ofInt(Integer.parseInt(normiert))));
        } catch (NumberFormatException zuGross) {
            // "12345678901" ist eine Zahl, aber kein int. Das als Tippfehler zu behandeln
            // waere falsch, deshalb wird es ein double.
            try {
                return Optional.of(NutzerAntwort.wert(Value.ofDouble(Double.parseDouble(normiert))));
            } catch (NumberFormatException keineZahl) {
                return Optional.empty();
            }
        }
    }
}
