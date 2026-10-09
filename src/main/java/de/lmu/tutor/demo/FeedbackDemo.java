package de.lmu.tutor.demo;

import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.diagnose.Diagnose;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.feedback.Rueckmeldung;

/**
 * Zeigt Schritt 6: den Feedback-Generator mit gestuftem Scaffolding.
 *
 * <p>Der Generator schreibt die Hinweise nicht selbst, sondern waehlt aus den in der Bug
 * Library hinterlegten Stufen die passende aus. Die Stufung folgt dem Prinzip nach Bruner
 * (1960): Stufe 0 lenkt die Aufmerksamkeit auf die richtige Stelle, ohne die Regel zu
 * nennen. Stufe 1 nennt die zugrundeliegende Java-Semantik. Stufe 2 wendet sie konkret auf
 * den Ausdruck an.</p>
 *
 * <p>Welche Stufe gilt, entscheidet allein die Wiederholungszahl, die von aussen
 * hereingereicht wird. Der Generator zaehlt selbst nichts mit. Das uebernimmt das
 * Studentenmodell, und diese Trennung macht ihn einzeln pruefbar.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.FeedbackDemo"  </pre>
 */
public final class FeedbackDemo {

    private static final BugLibrary LIB = BugLibrary.loadDefault();

    // Fester Seed, damit die Lobformulierungen bei jedem Lauf dieselben sind.
    private static final Feedbackgenerator GENERATOR = new Feedbackgenerator(new Random(2026));

    public static void main(String[] args) {
        System.out.println("Feedback-Generator - Demo (Schritt 6)");
        System.out.println("=".repeat(70));
        System.out.println();

        zeigeScaffolding("B01");
        zeigeScaffolding("B05");
        zeigeDeckelung();
        zeigeLoesungshilfe("B02");
        zeigeKorrekteAntworten();
        zeigeUnbekannteDiagnose();
        zeigeAlleKategorien();
    }

    // ---- Der Kern: Hinweise werden bei Wiederholung konkreter ----

    private static void zeigeScaffolding(String kategorieId) {
        Misconception m = LIB.byId(kategorieId).orElseThrow();
        System.out.println("-- " + m.id() + " " + m.name() + ", dreimal hintereinander --");
        System.out.println("   Beispiel: " + m.beispiel());
        System.out.println();

        for (int wiederholung = 0; wiederholung < 3; wiederholung++) {
            Rueckmeldung r = GENERATOR.erstelle(diagnoseZu(m), wiederholung);
            System.out.printf("  %d. Fehler, Stufe %d:%n", wiederholung + 1, r.stufe());
            System.out.println("     " + r.text());
        }
        System.out.println();
    }

    // ---- Nach der letzten Stufe bleibt es bei der Loesungshilfe ----

    private static void zeigeDeckelung() {
        Misconception m = LIB.byId("B01").orElseThrow();
        System.out.println("-- Was passiert beim 4., 5., 20. Fehler? --");
        System.out.println("   Die Stufen sind begrenzt; es bleibt bei der letzten.");
        System.out.println();

        for (int wiederholung : new int[]{3, 4, 20}) {
            Rueckmeldung r = GENERATOR.erstelle(diagnoseZu(m), wiederholung);
            System.out.printf("  %2d. Fehler  -> Stufe %d%n", wiederholung + 1, r.stufe());
        }
        System.out.println();
    }

    // ---- Loesung auf Wunsch, unabhaengig von der Wiederholungszahl ----

    private static void zeigeLoesungshilfe(String kategorieId) {
        Misconception m = LIB.byId(kategorieId).orElseThrow();
        System.out.println("-- Loesung auf Anforderung (" + m.id() + ") --");
        System.out.println("   Fuer einen Knopf 'Loesung zeigen', ohne vorherige Fehler.");
        System.out.println();

        Rueckmeldung r = GENERATOR.erstelleLoesungshilfe(m);
        System.out.printf("  Stufe %d: %s%n", r.stufe(), r.text());
        System.out.println();
    }

    // ---- Richtige Antworten ----

    private static void zeigeKorrekteAntworten() {
        System.out.println("-- Bestaetigung bei richtiger Antwort --");
        System.out.println("   Aus mehreren Formulierungen gewaehlt, damit es nicht");
        System.out.println("   bei jeder Aufgabe wortgleich klingt.");
        System.out.println();

        for (int i = 0; i < 5; i++) {
            Rueckmeldung r = GENERATOR.erstelle(Diagnose.korrekteAntwort(), 0);
            System.out.println("  " + r.text());
        }
        System.out.println();
    }

    // ---- Wenn keine Kategorie erkannt wurde ----

    private static void zeigeUnbekannteDiagnose() {
        System.out.println("-- Diagnose UNBEKANNT --");
        System.out.println("   Kein Fehlermuster passt. Statt eine falsche Kategorie zu");
        System.out.println("   behaupten, gibt es einen allgemein gehaltenen Hinweis.");
        System.out.println();

        Rueckmeldung r = GENERATOR.erstelle(Diagnose.unbekannt("kein Muster gefunden"), 0);
        System.out.println("  " + r.text());
        System.out.println("  Kategorie im Protokoll: "
                + (r.kategorieId().isEmpty() ? "keine" : r.kategorieId().get()));
        System.out.println();
    }

    // ---- Ueberblick ueber die ganze Bug Library ----

    private static void zeigeAlleKategorien() {
        System.out.println("=".repeat(70));
        System.out.println("Erste Stufe aller Kategorien");
        System.out.println();

        for (Misconception m : LIB.all()) {
            Rueckmeldung r = GENERATOR.erstelle(diagnoseZu(m), 0);
            System.out.printf("  %s (%d Stufen)  %s%n", m.id(), m.anzahlFeedbackStufen(), r.text());
        }
    }

    /** Eine Diagnose fuer diese Kategorie, wie sie der Fehlerklassifikator liefern wuerde. */
    private static Diagnose diagnoseZu(Misconception m) {
        return Diagnose.von(m, Diagnose.Konfidenz.SIMULIERT, "Beispiel fuer diese Demo");
    }
}
