package de.lmu.tutor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import de.lmu.tutor.ast.JType;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;

/**
 * Tests fuer die Uebersetzung der beiden Eingabefelder in einen typisierten Wert.
 *
 * <p>Diese Klasse ist wichtiger, als ihre Groesse vermuten laesst. Die gesamte Diagnose
 * haengt daran, dass Typ und Wert getrennt ankommen. Bei {@code 25 / 6} unterscheidet sich
 * die richtige Antwort von der haeufigsten falschen nur im Typ, und ein Parser, der hier
 * grosszuegig waere, wuerde die Fehlerklassifikation unbrauchbar machen.</p>
 */
class AntwortparserTest {

    private Value wert(String typ, String wert) {
        return Antwortparser.lies(typ, wert).orElseThrow().wert();
    }

    private Optional<NutzerAntwort> lies(String typ, String wert) {
        return Antwortparser.lies(typ, wert);
    }

    // ================================================================
    // Die Typliste
    // ================================================================

    @Test
    void dieListeEnthaeltDieTypenInJavaSchreibweise() {
        // So stehen sie auch in den Klausuraufgaben, also int und nicht Ganzzahl.
        assertEquals(List.of("int", "double", "char", "boolean", "String"),
                Antwortparser.typen());
    }

    // ================================================================
    // Die einzelnen Typen
    // ================================================================

    @Test
    void intNimmtGanzeZahlen() {
        assertEquals(Value.ofInt(7), wert("int", "7"));
        assertEquals(Value.ofInt(-3), wert("int", "-3"));
        assertEquals(Value.ofInt(0), wert("int", "0"));
    }

    @Test
    void intNimmtKeineKommazahl() {
        // Ein solches Paar gibt es in Java nicht, es laesst sich also auch nicht mit der
        // Musterloesung vergleichen.
        assertTrue(lies("int", "4.0").isEmpty());
    }

    @Test
    void doubleNimmtAuchGanzeZahlen() {
        // Wer double waehlt und 4 tippt, meint 4.0. Daran soll niemand scheitern.
        assertEquals(Value.ofDouble(4.0), wert("double", "4"));
        assertEquals(JType.DOUBLE, wert("double", "4").typ());
    }

    @Test
    void derTypKommtAusDemFeldUndNichtAusDerSchreibweise() {
        // Der Kern der Sache: 4 ist derselbe Zahlenwert, aber die Angabe des Typs
        // entscheidet. Wer bei 25 / 6 int und 4 waehlt, hat richtig gerechnet, wer
        // double und 4 waehlt, nicht.
        assertEquals(JType.INT, wert("int", "4").typ());
        assertEquals(JType.DOUBLE, wert("double", "4").typ());
    }

    @Test
    void einDezimalkommaWirdWieEinPunktGelesen() {
        // Zugestaendnis an die deutsche Tastaturgewohnheit, ohne Einfluss auf den Typ.
        assertEquals(Value.ofDouble(2.5), wert("double", "2,5"));
    }

    @Test
    void charNimmtEinZeichenMitUndOhneHochkomma() {
        assertEquals(Value.ofChar('a'), wert("char", "'a'"));
        assertEquals(Value.ofChar('a'), wert("char", "a"));
    }

    @Test
    void charNimmtKeineZweiZeichen() {
        assertTrue(lies("char", "ab").isEmpty());
    }

    @Test
    void booleanNimmtTrueUndFalse() {
        assertTrue(wert("boolean", "true").asBool());
        assertFalse(wert("boolean", "FALSE").asBool());
        assertTrue(lies("boolean", "ja").isEmpty());
    }

    @Test
    void stringNimmtDenTextMitUndOhneAnfuehrungszeichen() {
        assertEquals(Value.ofString("abc"), wert("String", "\"abc\""));
        assertEquals(Value.ofString("abc"), wert("String", "abc"));
        assertEquals(Value.ofString(""), wert("String", "\"\""));
    }

    @Test
    void beiStringBleibtEineZiffernfolgeText() {
        // Wichtig fuer die Stringkonkatenation: "33" + 2 ergibt "332" als String und
        // nicht die Zahl 332.
        assertEquals(Value.ofString("332"), wert("String", "332"));
        assertEquals(JType.STRING, wert("String", "332").typ());
    }

    @Test
    void nichtAuswertbarBrauchtKeinenWert() {
        assertFalse(lies(Antwortparser.NICHT_AUSWERTBAR, "").orElseThrow().auswertbar());
        assertFalse(lies("Nicht Auswertbar", null).orElseThrow().auswertbar());
    }

    // ================================================================
    // Was nicht gewertet wird
    // ================================================================

    @Test
    void ohneTypGibtEsKeineAntwort() {
        assertTrue(lies(null, "7").isEmpty());
        assertTrue(lies("", "7").isEmpty());
        assertTrue(lies("   ", "7").isEmpty());
    }

    @Test
    void ohneWertGibtEsKeineAntwort() {
        assertTrue(lies("int", null).isEmpty());
        assertTrue(lies("int", "").isEmpty());
    }

    @Test
    void einUnbekannterTypWirdZurueckgewiesen() {
        // Kann ueber die Oberflaeche nicht passieren, wohl aber ueber eine von Hand
        // zusammengebaute Anfrage. Dann soll nichts Unsinniges im Protokoll landen.
        assertTrue(lies("long", "7").isEmpty());
    }

    @Test
    void grossUndKleinschreibungDesTypsSpieltKeineRolle() {
        assertEquals(Value.ofInt(7), wert("INT", "7"));
        assertEquals(Value.ofString("a"), wert("string", "a"));
    }

    @Test
    void umgebendeLeerzeichenStoerenNicht() {
        assertEquals(Value.ofInt(7), wert("  int  ", "  7  "));
    }

    // ================================================================
    // Die Hinweise
    // ================================================================

    @Test
    void derHinweisNenntWasFehlt() {
        assertTrue(Antwortparser.hinweisZu("", "7").contains("Typ"));
        assertTrue(Antwortparser.hinweisZu("int", "").contains("Wert"));
    }

    @Test
    void derHinweisNenntDieRegelUndNichtDieAufgabe() {
        // Er darf nichts ueber den gestellten Ausdruck verraten, sonst waere er ein
        // verstecktes Feedback und die Kontrollgruppe bekaeme es ebenfalls.
        assertTrue(Antwortparser.hinweisZu("int", "4.0").contains("ganze Zahl"));
        assertTrue(Antwortparser.hinweisZu("boolean", "ja").contains("true"));
    }

    @Test
    void eineLesbareEingabeBrauchtKeinenHinweis() {
        assertTrue(Antwortparser.hinweisZu(Antwortparser.NICHT_AUSWERTBAR, "").isEmpty());
    }
}
