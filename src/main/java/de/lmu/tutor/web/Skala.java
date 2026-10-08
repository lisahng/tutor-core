package de.lmu.tutor.web;

import java.util.List;

/**
 * Die Skala, mit der die Rueckmeldung nach jeder Aufgabe bewertet wird.
 *
 * <p><b>Warum fuenf Stufen.</b> Die Zuverlaessigkeit einer Ratingskala steigt mit der Zahl
 * der Stufen bis etwa sieben und bleibt danach gleich, waehrend die Antwortzeit weiter
 * zunimmt (Preston und Colman 2000). Fuenf ist in diesem Bereich die sparsamste Wahl und
 * zugleich das Format, das der Fragebogen am Ende der Sitzung ebenfalls verwendet. Dieselbe
 * Skala an beiden Stellen erspart den Teilnehmenden das Umdenken und dir eine Umrechnung.</p>
 *
 * <p><b>Warum jede Stufe beschriftet ist.</b> Werden nur die Endpunkte benannt, legen
 * Befragte die mittleren Stufen unterschiedlich aus, und die Daten werden unschaerfer. Eine
 * durchgehende verbale Verankerung ist die empfohlene Form (Krosnick und Presser 2010).</p>
 *
 * <p><b>Warum nach Nuetzlichkeit und nicht nach gut oder schlecht gefragt wird.</b> Gut und
 * schlecht mischt zwei Dinge, naemlich ob die Rueckmeldung gefallen hat und ob sie
 * weitergeholfen hat. Narciss (2008, 2013) behandelt die wahrgenommene Nuetzlichkeit als
 * eigene Groesse, und sie ist die, die zur Forschungsfrage passt. Die Frage lautet deshalb
 * "Wie hilfreich war diese Rueckmeldung?" und nicht "Wie fandest du sie?".</p>
 *
 * <p><b>Warum dieselbe Frage auch in der Kontrollgruppe gestellt wird.</b> Sonst gaebe es
 * nichts zu vergleichen. Die Frage, das Aussehen und die Zahl der Klicks sind in beiden
 * Gruppen gleich, verschieden ist allein der Text, der bewertet wird. Damit bleibt der
 * Unterschied zwischen den Gruppen auch hier bei genau einer Sache.</p>
 */
public final class Skala {

    /** Eine Stufe der Skala. */
    public record Stufe(int wert, String text) {
    }

    /** Die Frage ueber der Skala. */
    public static final String FRAGE = "Wie hilfreich war diese Rückmeldung?";

    /** Die fuenf Stufen, von gar nicht hilfreich bis sehr hilfreich. */
    public static final List<Stufe> STUFEN = List.of(
            new Stufe(1, "gar nicht"),
            new Stufe(2, "wenig"),
            new Stufe(3, "teils teils"),
            new Stufe(4, "hilfreich"),
            new Stufe(5, "sehr hilfreich"));

    private Skala() {
    }
}
