package de.lmu.tutor.web;

import java.util.List;
import java.util.Optional;

import de.lmu.tutor.ast.JType;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;

/**
 * Uebersetzt die beiden Eingabefelder aus dem Browser in eine {@link NutzerAntwort}.
 *
 * <p>Gefragt wird nach Typ und Wert, getrennt, genau wie in den Klausuraufgaben. Das ist
 * nicht nur eine Frage der Vertrautheit. Die Diagnose unterscheidet ohnehin zwischen einem
 * falschen Wert und einem falschen Typ, und wer beides einzeln angibt, legt diese
 * Unterscheidung selbst offen. Bei {@code 25 / 6} zeigt die Wahl zwischen {@code int} und
 * {@code double} unmittelbar, ob die Integer-Division verstanden wurde.</p>
 *
 * <p><b>Warum der Typ aus einer Liste gewaehlt wird.</b> Frei getippt kaemen {@code int},
 * {@code Int}, {@code Integer} und {@code ganze Zahl} nebeneinander in den Logdaten vor,
 * und bei zwanzig Teilnehmenden muesste das vor der Auswertung von Hand vereinheitlicht
 * werden. Die Liste nimmt niemandem etwas ab, denn die infrage kommenden Typen sind aus
 * dem Unterricht bekannt, und sie macht die Angabe zu einer bewussten Entscheidung.</p>
 *
 * <p><b>Warum eine unleserliche Eingabe kein Fehlversuch ist.</b> Wer sich vertippt, hat
 * keine Fehlvorstellung gezeigt. Wuerde ein Tippfehler als falsche Antwort zaehlen, liefe
 * das Scaffolding eine Stufe weiter und die Fehlerhistorie des Studentenmodells bekaeme
 * einen Eintrag, der nichts ueber den Kenntnisstand aussagt. Der Parser liefert dann
 * {@link Optional#empty()}, und die Oberflaeche bittet um eine neue Eingabe.</p>
 *
 * <p>Dazu gehoert auch der Fall, dass Typ und Wert nicht zusammenpassen, etwa {@code int}
 * mit {@code 4.0}. Ein solches Paar gibt es in Java nicht, es laesst sich also auch nicht
 * mit der Musterloesung vergleichen. Der Hinweis dazu nennt nur die Java-Regel und sagt
 * nichts ueber die gestellte Aufgabe. Beide Gruppen bekommen ihn wortgleich.</p>
 */
public final class Antwortparser {

    /** Der Eintrag in der Typliste fuer Ausdruecke, die zur Laufzeit scheitern. */
    public static final String NICHT_AUSWERTBAR = "nicht auswertbar";

    /** Die Typen, die in der Oberflaeche zur Auswahl stehen, in der Reihenfolge der Liste. */
    private static final List<JType> AUSWAHL =
            List.of(JType.INT, JType.DOUBLE, JType.CHAR, JType.BOOL, JType.STRING);

    private Antwortparser() {
    }

    /** Die Typnamen fuer das Auswahlfeld, also int, double, char, boolean, String. */
    public static List<String> typen() {
        return AUSWAHL.stream().map(JType::javaName).toList();
    }

    /**
     * Liest die beiden Felder als Java-Wert.
     *
     * @param typEingabe  der gewaehlte Typ, etwa {@code "int"}, oder {@link #NICHT_AUSWERTBAR}
     * @param wertEingabe der getippte Wert, bei {@link #NICHT_AUSWERTBAR} ohne Bedeutung
     * @return die Antwort, oder leer, wenn die Eingabe nicht lesbar war
     */
    public static Optional<NutzerAntwort> lies(String typEingabe, String wertEingabe) {
        String typ = typEingabe == null ? "" : typEingabe.trim();
        if (typ.isEmpty()) {
            return Optional.empty();
        }
        if (typ.equalsIgnoreCase(NICHT_AUSWERTBAR)) {
            return Optional.of(NutzerAntwort.nichtAuswertbar());
        }

        Optional<JType> gewaehlt = zuTyp(typ);
        if (gewaehlt.isEmpty()) {
            return Optional.empty();
        }

        String wert = wertEingabe == null ? "" : wertEingabe.trim();
        if (wert.isEmpty()) {
            return Optional.empty();
        }
        return lieseWert(gewaehlt.get(), wert).map(NutzerAntwort::wert);
    }

    /**
     * Sagt in einem Satz, warum eine Eingabe nicht gelesen werden konnte.
     *
     * <p>Der Text nennt nur die Java-Regel, nie etwas ueber die gestellte Aufgabe, und ist
     * in beiden Gruppen derselbe.</p>
     */
    public static String hinweisZu(String typEingabe, String wertEingabe) {
        String typ = typEingabe == null ? "" : typEingabe.trim();
        if (typ.isEmpty()) {
            return "Bitte waehle zuerst einen Typ aus.";
        }
        if (typ.equalsIgnoreCase(NICHT_AUSWERTBAR)) {
            return "";
        }
        if (zuTyp(typ).isEmpty()) {
            return "Diesen Typ kenne ich nicht. Waehle einen aus der Liste.";
        }

        String wert = wertEingabe == null ? "" : wertEingabe.trim();
        if (wert.isEmpty()) {
            return "Bitte trag auch einen Wert ein. Fuer die leere Zeichenkette gilt \"\".";
        }
        return switch (zuTyp(typ).get()) {
            case INT -> "Zu int passt nur eine ganze Zahl ohne Punkt, etwa 7 oder -3.";
            case DOUBLE -> "Zu double passt eine Zahl, etwa 2.5 oder 4.0.";
            case CHAR -> "Zu char passt genau ein Zeichen, etwa 'a' oder a.";
            case BOOL -> "Zu boolean passt true oder false.";
            default -> "Diese Eingabe konnte ich nicht lesen.";
        };
    }

    // ================================================================
    // Innenleben
    // ================================================================

    /** Findet den Typ zum Namen, unabhaengig von Gross- und Kleinschreibung. */
    private static Optional<JType> zuTyp(String name) {
        return AUSWAHL.stream().filter(t -> t.javaName().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * Liest den Wert so, wie der gewaehlte Typ es verlangt.
     *
     * <p>Anfuehrungszeichen duerfen stehen oder fehlen. Wer bei {@code String} den Wert
     * {@code "Java"} eintippt, meint dasselbe wie jemand, der {@code Java} tippt, und an
     * dieser Formalie soll niemand scheitern. Bei {@code double} wird ein Dezimalkomma wie
     * ein Punkt gelesen, als Zugestaendnis an die deutsche Tastaturgewohnheit.</p>
     */
    private static Optional<Value> lieseWert(JType typ, String wert) {
        return switch (typ) {
            case INT -> ganzeZahl(wert);
            case DOUBLE -> kommazahl(wert);
            case CHAR -> zeichen(wert);
            case BOOL -> wahrheitswert(wert);
            case STRING -> Optional.of(Value.ofString(ohneKlammern(wert, '"')));
            default -> Optional.empty();
        };
    }

    private static Optional<Value> ganzeZahl(String wert) {
        try {
            return Optional.of(Value.ofInt(Integer.parseInt(wert)));
        } catch (NumberFormatException keineGanzeZahl) {
            return Optional.empty();
        }
    }

    private static Optional<Value> kommazahl(String wert) {
        try {
            return Optional.of(Value.ofDouble(Double.parseDouble(wert.replace(',', '.'))));
        } catch (NumberFormatException keineZahl) {
            return Optional.empty();
        }
    }

    private static Optional<Value> zeichen(String wert) {
        String inhalt = ohneKlammern(wert, '\'');
        return inhalt.length() == 1
                ? Optional.of(Value.ofChar(inhalt.charAt(0)))
                : Optional.empty();
    }

    private static Optional<Value> wahrheitswert(String wert) {
        if (wert.equalsIgnoreCase("true")) {
            return Optional.of(Value.ofBool(true));
        }
        if (wert.equalsIgnoreCase("false")) {
            return Optional.of(Value.ofBool(false));
        }
        return Optional.empty();
    }

    /** Entfernt ein Paar umschliessender Anfuehrungszeichen, falls vorhanden. */
    private static String ohneKlammern(String wert, char zeichen) {
        if (wert.length() >= 2 && wert.charAt(0) == zeichen
                && wert.charAt(wert.length() - 1) == zeichen) {
            return wert.substring(1, wert.length() - 1);
        }
        return wert;
    }
}
