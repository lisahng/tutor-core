package de.lmu.tutor.demo;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Difficulty;
import de.lmu.tutor.buglib.Misconception;

/**
 * Kleine Demo: Sie laedt die Bug Library und gibt einen Ueberblick aus. 
 */
public final class BugLibraryDemo {

    public static void main(String[] args) {
        BugLibrary lib = BugLibrary.loadDefault();

        System.out.println("=== Bug Library geladen ===");
        System.out.println("Anzahl Kategorien: " + lib.size());
        System.out.println();

        System.out.printf("%-5s %-38s %-8s %4s %5s %4s%n",
                "ID", "Name", "Schwer.", "b(K)", "Fb.", "Sub");
        System.out.println("-".repeat(70));
        for (Misconception m : lib.all()) {
            System.out.printf("%-5s %-38s %-8s %4d %5d %4d%n",
                    m.id(),
                    kuerze(m.name(), 38),
                    m.schwierigkeit(),
                    m.basisgewicht(),
                    m.anzahlFeedbackStufen(),
                    m.untertypen().size());
        }
        System.out.println();

        // Beispiel: gestuftes Feedback einer Kategorie
        lib.byId("B05").ifPresent(m -> {
            System.out.println("Gestuftes Feedback fuer " + m.id() + " (" + m.name() + "):");
            for (int stufe = 0; stufe < m.anzahlFeedbackStufen(); stufe++) {
                System.out.println("  Stufe " + stufe + ": " + m.feedbackStufe(stufe));
            }
        });
        System.out.println();

        // Beispiel: Aufgabengewichtung w(K,s) = b(K) * (1 + alpha * f)
        double alpha = 1.0;
        System.out.println("Auswahlgewicht B01 (Basisgewicht 5), alpha = " + alpha + ":");
        for (double f : new double[]{0.0, 0.25, 0.5, 1.0}) {
            System.out.printf("  f = %.2f  ->  w = %.2f%n",
                    f, lib.auswahlGewicht("B01", f, alpha));
        }
        System.out.println();

        System.out.println("Kategorien nach Schwierigkeit:");
        for (Difficulty d : Difficulty.values()) {
            System.out.println("  " + d + " (Stufe " + d.stufe() + "): "
                    + lib.byDifficulty(d).size() + " Kategorien");
        }
    }

    private static String kuerze(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }
}
