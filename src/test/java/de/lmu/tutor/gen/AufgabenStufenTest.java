package de.lmu.tutor.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.diagnose.Fehlerklassifikator;
import de.lmu.tutor.diagnose.Fehlersimulator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.session.Uebungssitzung;

/**
 * Prueft die Schwierigkeitsstufen des Aufgabengenerators.
 *
 * <p>Eine schwerere Aufgabe nuetzt nichts, wenn das System die Fehlvorstellung darin nicht
 * mehr erkennt. Genau das ist der Grund, warum nicht jede Kategorie in jeder Stufe
 * vorkommt: Die Regeln des Fehlersimulators setzen an der Wurzel des Ausdrucks an, und
 * sobald ein weiterer Schritt darueberliegt, greifen sie nicht mehr. Welche Kombination
 * das uebersteht, ist gemessen und nicht geschaetzt.</p>
 *
 * <p>Dieser Test haelt die Messung fest. Wird der Generator erweitert, faellt sofort auf,
 * wenn eine Aufgabe zwar schwerer, aber nicht mehr diagnostizierbar ist.</p>
 */
class AufgabenStufenTest {

    private static final int STICHPROBE = 100;

    /** Kategorien, deren Fehlvorstellung der Simulator nachrechnet. */
    private static final List<String> SIMULIERT =
            List.of("B01", "B02", "B03", "B04", "B05", "B06", "B10", "B11", "B12");

    /** Kategorien ohne Simulationsregel, erkannt ueber Typ, Auswertbarkeit oder Antwort. */
    private static final List<String> OHNE_SIMULATION = List.of("B07", "B08", "B09", "B13");

    private final BugLibrary lib = BugLibrary.loadDefault();
    private final Fehlersimulator simulator = new Fehlersimulator();
    private final StepEvaluator evaluator = new StepEvaluator();

    // ================================================================
    // Die Steigerung selbst
    // ================================================================

    @Test
    void dieStufeHaengtAnDerPositionUndNichtAnDerLeistung() {
        // Nach Leistung waere auch die Kontrollbedingung angepasst, und der untersuchte
        // Unterschied zwischen den Gruppen wuerde kleiner.
        assertEquals(1, Uebungssitzung.stufeFuer(1));
        assertEquals(1, Uebungssitzung.stufeFuer(6));
        assertEquals(2, Uebungssitzung.stufeFuer(7));
        assertEquals(2, Uebungssitzung.stufeFuer(14));
        assertEquals(3, Uebungssitzung.stufeFuer(15));
        assertEquals(3, Uebungssitzung.stufeFuer(60));
    }

    @Test
    void spaetereAufgabenSindGroesserAlsFruehe() {
        // Ohne diese Pruefung koennte die Stufe steigen, ohne dass sich etwas aendert.
        int klein = 0;
        int gross = 0;
        AufgabenGenerator gen = new AufgabenGenerator(99L);
        for (String id : alleKategorien()) {
            for (int i = 0; i < 20; i++) {
                klein += gen.generiere(id, 1).ausdruck().groesse();
                gross += gen.generiere(id, 3).ausdruck().groesse();
            }
        }
        assertTrue(gross > klein * 5 / 4,
                "Stufe 3 sollte deutlich mehr Schritte haben, war " + gross + " gegen " + klein);
    }

    @Test
    void eineUnbekannteStufeWirdBegrenztStattZuScheitern() {
        AufgabenGenerator gen = new AufgabenGenerator(1L);
        assertEquals(gen.generiere("B01", 3).kategorieId(), gen.generiere("B01", 9).kategorieId());
        assertEquals(gen.generiere("B01", 1).kategorieId(), gen.generiere("B01", -4).kategorieId());
    }

    // ================================================================
    // Auswertbarkeit
    // ================================================================

    @Test
    void jedeAufgabeIstAuswertbarAusserB08() {
        for (String id : alleKategorien()) {
            for (int stufe = 1; stufe <= 3; stufe++) {
                AufgabenGenerator gen = new AufgabenGenerator(4711L + stufe);
                for (int i = 0; i < STICHPROBE; i++) {
                    Aufgabe aufgabe = gen.generiere(id, stufe);
                    EvaluationResult r = evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
                    if (id.equals("B08")) {
                        assertFalse(r.auswertbar(),
                                "B08 muss unauswertbar sein: " + aufgabe.render());
                    } else {
                        assertTrue(r.auswertbar(),
                                id + " Stufe " + stufe + " nicht auswertbar: " + aufgabe.render());
                    }
                }
            }
        }
    }

    @Test
    void dieBelegungNenntNurVariablenDieImAusdruckVorkommen() {
        // Die Vorlagen legen k und i gemeinsam an, weil mehrere Stufen sich denselben
        // Kontext teilen. Angezeigt werden darf nur, was im Ausdruck auch steht. Eine
        // ueberzaehlige Variable laesst die Person zuerst ihre eigene Lesart pruefen, und
        // diese Zeit steht hinterher im Protokoll als Bearbeitungsdauer der Aufgabe.
        for (String id : alleKategorien()) {
            for (int stufe = 1; stufe <= 3; stufe++) {
                AufgabenGenerator gen = new AufgabenGenerator(31L + stufe);
                for (int lauf = 0; lauf < STICHPROBE; lauf++) {
                    Aufgabe aufgabe = gen.generiere(id, stufe);
                    String text = aufgabe.render();
                    for (String name : List.of("a", "s", "k", "i", "x")) {
                        boolean imAusdruck = stehtImBaum(aufgabe.ausdruck(), name);
                        boolean inDerBelegung = aufgabe.belegung().startsWith(name + " = ")
                                || aufgabe.belegung().contains(", " + name + " = ");
                        assertEquals(imAusdruck, inDerBelegung,
                                id + " Stufe " + stufe + ": " + text
                                        + " mit Belegung [" + aufgabe.belegung()
                                        + "], Variable " + name);
                    }
                }
            }
        }
    }

    // ================================================================
    // Diagnostizierbarkeit
    // ================================================================

    @Test
    void dieFehlvorstellungFuehrtInJederStufeZuEinemAnderenErgebnis() {
        for (String id : SIMULIERT) {
            for (int stufe = 1; stufe <= 3; stufe++) {
                int quote = anteilDiagnostisch(id, stufe);
                assertTrue(quote >= 95,
                        id + " Stufe " + stufe + " nur zu " + quote
                                + " Prozent diagnostisch. Entweder die Bauform zuruecknehmen "
                                + "oder die Stufe in hoechsteGepruefteStufe ausschliessen.");
            }
        }
    }

    @Test
    void beiB14BleibtNurDerGleichheitsfallDiagnostisch() {
        // Die Fehlvorstellung lautet, equals vergleiche Referenzen und liefere false. Ist
        // die richtige Antwort ohnehin false, fuehrt sie zum selben Wert. Das liegt an der
        // Fehlvorstellung und nicht an der Bauform, deshalb hier eine eigene Schwelle.
        for (int stufe = 1; stufe <= 3; stufe++) {
            int quote = anteilDiagnostisch("B14", stufe);
            assertTrue(quote >= 40 && quote <= 70,
                    "B14 Stufe " + stufe + " lag bei " + quote + " Prozent");
        }
    }

    @Test
    void dieKategorienOhneSimulationWerdenUeberDieAntwortErkannt() {
        Fehlerklassifikator klassifikator = new Fehlerklassifikator(lib);
        for (String id : OHNE_SIMULATION) {
            for (int stufe = 1; stufe <= 3; stufe++) {
                AufgabenGenerator gen = new AufgabenGenerator(7L + stufe);
                int treffer = 0;
                int gezaehlt = 0;
                for (int i = 0; i < STICHPROBE; i++) {
                    Aufgabe aufgabe = gen.generiere(id, stufe);
                    EvaluationResult r = evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
                    NutzerAntwort falsch = typischeFalschantwort(id, r);
                    if (falsch == null) {
                        continue;
                    }
                    gezaehlt++;
                    Diagnose d = klassifikator.diagnostiziere(aufgabe, r, falsch);
                    if (d.kategorien().stream().anyMatch(k -> k.id().equals(id))) {
                        treffer++;
                    }
                }
                assertTrue(gezaehlt > 0, id + " Stufe " + stufe + ": keine pruefbare Antwort");
                assertEquals(gezaehlt, treffer,
                        id + " Stufe " + stufe + ": nur " + treffer + " von " + gezaehlt
                                + " erkannt");
            }
        }
    }

    // ================================================================
    // Hilfsmittel
    // ================================================================

    private List<String> alleKategorien() {
        return lib.all().stream().map(m -> m.id()).toList();
    }

    /**
     * Ob die Variable mit diesem Namen im Ausdruck steht. Geprueft wird der Baum und nicht
     * der Quelltext: In {@code "Java".charAt(14) == 'a'} steht ein a, aber als Zeichen.
     */
    private boolean stehtImBaum(Expr ausdruck, String name) {
        if (ausdruck instanceof Expr.Var v && v.name().equals(name)) {
            return true;
        }
        return ausdruck.children().stream().anyMatch(kind -> stehtImBaum(kind, name));
    }

    /**
     * Anteil der Aufgaben, bei denen die Fehlvorstellung zu einem anderen Ergebnis fuehrt
     * als die richtige Loesung. Nur dann ist sie an der Antwort zu erkennen.
     */
    private int anteilDiagnostisch(String id, int stufe) {
        AufgabenGenerator gen = new AufgabenGenerator(42L + stufe);
        int diagnostisch = 0;
        for (int i = 0; i < STICHPROBE; i++) {
            Aufgabe aufgabe = gen.generiere(id, stufe);
            EvaluationResult richtig = evaluator.evaluate(aufgabe.ausdruck(), aufgabe.kontext());
            boolean passt = simulator.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext()).stream()
                    .filter(t -> t.bugIds().equals(List.of(id)))
                    .anyMatch(t -> !richtig.auswertbar()
                            || !t.wert().mitTyp().equals(richtig.wert().mitTyp()));
            if (passt) {
                diagnostisch++;
            }
        }
        return diagnostisch * 100 / STICHPROBE;
    }

    /** Die Antwort, die jemand mit genau dieser Fehlvorstellung geben wuerde. */
    private NutzerAntwort typischeFalschantwort(String id, EvaluationResult r) {
        return switch (id) {
            // Wert richtig, Typ falsch angegeben.
            case "B07" -> !r.auswertbar() || r.wert().typ() != JType.INT
                    ? null : NutzerAntwort.wert(Value.ofDouble(r.wert().asDouble()));
            // Das Zeichen als Zeichenkette angegeben.
            case "B13" -> !r.auswertbar() || r.wert().typ() != JType.CHAR
                    ? null : NutzerAntwort.wert(Value.ofString(String.valueOf(r.wert().asChar())));
            // Ein Wert angegeben, obwohl der Ausdruck nicht auswertbar ist.
            case "B08" -> NutzerAntwort.wert(Value.ofInt(1));
            // "Nicht auswertbar" angegeben, obwohl die Kurzschlussauswertung rettet.
            case "B09" -> NutzerAntwort.nichtAuswertbar();
            default -> null;
        };
    }
}
