package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import de.lmu.tutor.ast.JType;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;

/**
 * Tests fuer die Uebersetzung der Browsereingabe in einen typisierten Wert.
 *
 * <p>Diese Klasse ist wichtiger, als ihre Groesse vermuten laesst. Die gesamte Diagnose
 * haengt daran, dass {@code 4} und {@code 4.0} unterschiedlich ankommen, denn genau dieser
 * Unterschied macht die Fehlvorstellung zur Integer-Division sichtbar. Ein Parser, der hier
 * grosszuegig waere, wuerde die Fehlerklassifikation unbrauchbar machen.</p>
 */
class AntwortparserTest {

    private Value lies(String eingabe) {
        return Antwortparser.lies(eingabe, Optional.empty()).orElseThrow().wert();
    }

    private Optional<NutzerAntwort> liesRoh(String eingabe) {
        return Antwortparser.lies(eingabe, Optional.empty());
    }

    private Optional<NutzerAntwort> liesMitTyp(String eingabe, JType erwartet) {
        return Antwortparser.lies(eingabe, Optional.of(erwartet));
    }

    // ================================================================
    // Zahlen
    // ================================================================

    @Test
    void ganzeZahlenWerdenInt() {
        assertEquals(Value.ofInt(7), lies("7"));
        assertEquals(Value.ofInt(-3), lies("-3"));
        assertEquals(Value.ofInt(0), lies("0"));
    }

    @Test
    void zahlenMitPunktWerdenDouble() {
        assertEquals(Value.ofDouble(2.5), lies("2.5"));
        assertEquals(JType.DOUBLE, lies("4.0").typ());
    }

    @Test
    void derTypHaengtAmPunktUndNichtAmWert() {
        // Der Kern der Sache: 4 und 4.0 sind derselbe Zahlenwert, aber verschiedene Typen.
        // Wer bei 25 / 6 "4" eingibt, hat richtig gerechnet, wer "4.0" eingibt, nicht.
        assertEquals(JType.INT, lies("4").typ());
        assertEquals(JType.DOUBLE, lies("4.0").typ());
    }

    @Test
    void einDezimalkommaWirdWieEinPunktGelesen() {
        // Zugestaendnis an die deutsche Tastaturgewohnheit. Der Typ bleibt double, es wird
        // also nichts verraten, sondern nur eine unnoetige Huerde abgebaut.
        assertEquals(Value.ofDouble(2.5), lies("2,5"));
    }

    @Test
    void zuGrosseGanzeZahlenWerdenDouble() {
        // "12345678901" ist eine Zahl, nur eben kein int. Das als Tippfehler abzuweisen
        // waere falsch, denn die Person hat sehr wohl einen Wert gemeint.
        assertEquals(JType.DOUBLE, lies("12345678901").typ());
    }

    // ================================================================
    // Uebrige Typen
    // ================================================================

    @Test
    void wahrheitswerteWerdenBool() {
        assertTrue(lies("true").asBool());
        assertFalse(lies("false").asBool());
    }

    @Test
    void grossUndKleinschreibungSpieltBeiWahrheitswertenKeineRolle() {
        assertTrue(lies("TRUE").asBool());
        assertFalse(lies("False").asBool());
    }

    @Test
    void einfacheAnfuehrungszeichenErgebenChar() {
        assertEquals(Value.ofChar('a'), lies("'a'"));
    }

    @Test
    void doppelteAnfuehrungszeichenErgebenString() {
        assertEquals(Value.ofString("abc"), lies("\"abc\""));
        assertEquals(Value.ofString(""), lies("\"\""));
    }

    @Test
    void mehrereZeichenInEinfachenAnfuehrungszeichenSindKeinChar() {
        assertTrue(liesRoh("'ab'").isEmpty());
    }

    @Test
    void nichtAuswertbarWirdErkannt() {
        assertFalse(liesRoh("nicht auswertbar").orElseThrow().auswertbar());
        assertFalse(liesRoh("Nicht Auswertbar").orElseThrow().auswertbar());
        assertFalse(liesRoh("fehler").orElseThrow().auswertbar());
    }

    // ================================================================
    // Was nicht gewertet wird
    // ================================================================

    @Test
    void leereEingabenWerdenZurueckgewiesen() {
        assertTrue(liesRoh(null).isEmpty());
        assertTrue(liesRoh("").isEmpty());
        assertTrue(liesRoh("   ").isEmpty());
    }

    @Test
    void unlesbareEingabenWerdenZurueckgewiesen() {
        // Wichtig fuer die Fairness: Wer sich vertippt, hat keine Fehlvorstellung gezeigt.
        // Der Ablauf wertet eine solche Eingabe deshalb gar nicht erst.
        assertTrue(liesRoh("haehae").isEmpty());
        assertTrue(liesRoh("??").isEmpty());
    }

    @Test
    void umgebendeLeerzeichenStoerenNicht() {
        assertEquals(Value.ofInt(7), lies("  7  "));
    }

    // ================================================================
    // Der Rueckfall auf Zeichenketten
    // ================================================================

    @Test
    void ohneAnfuehrungszeichenGiltStringNurWennDieLoesungEinStringIst() {
        assertEquals(Value.ofString("haehae"),
                liesMitTyp("haehae", JType.STRING).orElseThrow().wert());
        assertTrue(liesMitTyp("haehae", JType.INT).isEmpty());
    }

    @Test
    void derRueckfallVerraetNichts() {
        // Auch bei einer String-Loesung bleibt "7" ein int. Sonst bekaeme die Person die
        // Rueckmeldung zum Typfehler nicht, obwohl sie genau den gemacht hat.
        assertEquals(JType.INT, liesMitTyp("7", JType.STRING).orElseThrow().wert().typ());
    }

    @Test
    void anfuehrungszeichenHabenVorrang() {
        assertEquals(Value.ofString("true"),
                liesMitTyp("\"true\"", JType.STRING).orElseThrow().wert());
    }
}
