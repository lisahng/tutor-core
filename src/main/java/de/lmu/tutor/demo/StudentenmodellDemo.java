package de.lmu.tutor.demo;

import java.util.ArrayList;
import java.util.List;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.student.Kategoriestatistik;
import de.lmu.tutor.student.PfaParameter;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Zeigt das Studentenmodell: Fehlerhistorie, PFA-Schaetzung und Aufgabenauswahl.
 *
 * <p>Das Modell schaetzt fuer jede Kategorie, wie wahrscheinlich die naechste Aufgabe
 * richtig geloest wird:</p>
 *
 * <pre>
 *   m = beta + gamma * erfolge + rho * fehler
 *   P(richtig) = 1 / (1 + e^(-m))
 * </pre>
 *
 * <p>Geuebt wird immer die Kategorie mit dem kleinsten P. Weil ein Fehler P weiter senkt,
 * waere dieselbe Kategorie sonst sofort wieder an der Reihe. Dagegen gibt es die
 * Wiederholungssperre, deren Wirkung dieser Demo im dritten Abschnitt zeigt.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.StudentenmodellDemo"  </pre>
 */
public final class StudentenmodellDemo {

    private static final BugLibrary LIB = BugLibrary.loadDefault();

    public static void main(String[] args) {
        System.out.println("Studentenmodell - Demo");
        System.out.println("=".repeat(70));
        System.out.println();

        zeigeStartzustand();
        zeigeWirkungVonErfolgUndFehler();
        zeigeWiederholungenFuerDasFeedback();
        zeigeAuswahlOhneSperre();
        zeigeAuswahlMitSperre();
        zeigeAdaptivitaet();
    }

    // ---- Ausgangslage ----

    private static void zeigeStartzustand() {
        System.out.println("-- Ausgangslage --");
        System.out.println("   Solange beta nicht aus Daten geschaetzt ist, gilt fuer alle");
        System.out.println("   Kategorien derselbe Platzhalter. Jede startet bei P = 0.");
        System.out.println();

        Studentenmodell modell = new Studentenmodell(LIB);
        for (Misconception m : LIB.all().subList(0, 4)) {
            System.out.printf("  %s  beta = %.2f  P = %.3f%n",
                    m.id(), m.beta(), modell.erfolgswahrscheinlichkeit(m.id()));
        }
        System.out.println("  ... (alle uebrigen ebenso)");
        System.out.println();
    }

    // ---- Wie sich P veraendert ----

    private static void zeigeWirkungVonErfolgUndFehler() {
        System.out.println("-- Wirkung von Erfolgen und Fehlern (B01) --");
        System.out.printf("   gamma = %.1f je Erfolg, rho = %.1f je Fehler%n",
                PfaParameter.STANDARD.gamma(), PfaParameter.STANDARD.rho());
        System.out.println("   Ein Erfolg wiegt also schwerer als ein Fehler.");
        System.out.println();

        Studentenmodell modell = new Studentenmodell(LIB);
        System.out.printf("  Start                      P = %.3f%n", modell.erfolgswahrscheinlichkeit("B01"));

        for (int i = 1; i <= 3; i++) {
            modell.erfasseErgebnis("B01", true);
            System.out.printf("  nach %d Erfolgen            P = %.3f%n",
                    i, modell.erfolgswahrscheinlichkeit("B01"));
        }
        for (int i = 1; i <= 3; i++) {
            modell.erfasseErgebnis("B01", false);
            System.out.printf("  dazu %d Fehler              P = %.3f%n",
                    i, modell.erfolgswahrscheinlichkeit("B01"));
        }
        System.out.println("  " + modell.statistik("B01"));
        System.out.println();
    }

    // ---- Schnittstelle zum Feedback-Generator ----

    private static void zeigeWiederholungenFuerDasFeedback() {
        System.out.println("-- Wiederholungszahl fuer die Scaffolding-Stufe --");
        System.out.println("   erfasseUndGibWiederholungen liefert den Stand VOR dem Versuch.");
        System.out.println("   Sonst zaehlte der aktuelle Fehler schon mit und Stufe 0 entfiele.");
        System.out.println();

        Studentenmodell modell = new Studentenmodell(LIB);
        for (int versuch = 1; versuch <= 4; versuch++) {
            int stufe = modell.erfasseUndGibWiederholungen("B05", false);
            System.out.printf("  %d. Fehler  -> Feedback-Stufe %d%n", versuch, stufe);
        }
        System.out.println("  Ein Erfolg dazwischen aendert die Stufe nicht:");
        modell.erfasseErgebnis("B05", true);
        System.out.printf("  nach einem Erfolg -> Stufe %d, Erfolge: %d, Fehler: %d%n",
                modell.wiederholungen("B05"), modell.erfolge("B05"), modell.fehler("B05"));
        System.out.println();
    }

    // ---- Das Problem ----

    private static void zeigeAuswahlOhneSperre() {
        System.out.println("-- Auswahl ohne Sperre --");
        System.out.println("   Jeder Fehler senkt P, also bleibt dieselbe Kategorie vorn.");
        System.out.println();

        Studentenmodell modell = new Studentenmodell(LIB, PfaParameter.STANDARD, 0);
        System.out.println("  Gestellt: " + zehnAufgaben(modell));
        System.out.println();
    }

    // ---- Die Loesung ----

    private static void zeigeAuswahlMitSperre() {
        for (int sperre : new int[]{1, 3}) {
            System.out.println("-- Auswahl mit Sperre von " + sperre + " --");
            Studentenmodell modell = new Studentenmodell(LIB, PfaParameter.STANDARD, sperre);
            System.out.println("  Gestellt: " + zehnAufgaben(modell));
            System.out.println("  Aktuell gesperrt: " + modell.gesperrteKategorien());
            System.out.println();
        }
    }

    /** Stellt zehn Aufgaben, alle falsch beantwortet, und gibt die Reihenfolge zurueck. */
    private static String zehnAufgaben(Studentenmodell modell) {
        List<String> reihenfolge = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Misconception gewaehlt = modell.naechsteKategorie().orElseThrow();
            reihenfolge.add(gewaehlt.id());
            modell.erfasseErgebnis(gewaehlt.id(), false);
        }
        return String.join(" -> ", reihenfolge);
    }

    // ---- Adaptivitaet ----

    private static void zeigeAdaptivitaet() {
        System.out.println("=".repeat(70));
        System.out.println("Adaptivitaet: eine Person, die nur bei B05 scheitert");
        System.out.println();

        Studentenmodell modell = new Studentenmodell(LIB, PfaParameter.STANDARD, 1);

        // Ueberall Erfolge sammeln, nur bei B05 nicht.
        for (Misconception m : LIB.all()) {
            if (m.id().equals("B05")) {
                modell.erfasseErgebnis("B05", false);
                modell.erfasseErgebnis("B05", false);
            } else {
                modell.erfasseErgebnis(m.id(), true);
                modell.erfasseErgebnis(m.id(), true);
            }
        }

        System.out.println("  Kenntnisstand:");
        for (Kategoriestatistik stand : modell.alleStatistiken()) {
            System.out.println("    " + stand);
        }
        System.out.println();
        System.out.println("  Naechste Aufgabe: " + modell.naechsteKategorie().orElseThrow().id()
                + "  (die schwaechste Kategorie)");
    }
}