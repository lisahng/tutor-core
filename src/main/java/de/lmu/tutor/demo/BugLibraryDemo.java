package de.lmu.tutor.demo;

import java.util.HashMap;
import java.util.Map;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;

/**
 * Demo zu Schritt 1: laedt die Bug Library und zeigt die Aufgabenauswahl
 * nach PFA (Pavlik et al. 2009).
 */
public final class BugLibraryDemo {

    // PFA-Modellparameter -- PLATZHALTER, aus Pilotdaten zu schaetzen.
    private static final double GAMMA = 0.4;
    private static final double RHO = -0.8;

    public static void main(String[] args) {
        BugLibrary lib = BugLibrary.loadDefault();

        System.out.println("=== Bug Library geladen ===");
        System.out.println("Anzahl Kategorien: " + lib.size());
        System.out.println();

        System.out.printf("%-5s %-40s %6s %5s %4s%n", "ID", "Name", "beta", "Fb.", "Sub");
        System.out.println("-".repeat(64));
        for (Misconception m : lib.all()) {
            System.out.printf("%-5s %-40s %6.1f %5d %4d%n",
                    m.id(),
                    kuerze(m.name(), 40),
                    m.beta(),
                    m.anzahlFeedbackStufen(),
                    m.untertypen().size());
        }
        System.out.println();

        // PFA: Erfolgswahrscheinlichkeit in Abhaengigkeit von Erfolgen/Fehlern
        System.out.println("PFA-Erfolgswahrscheinlichkeit fuer B05 (gamma=" + GAMMA + ", rho=" + RHO + "):");
        int[][] faelle = {{0, 0}, {0, 1}, {0, 3}, {2, 0}, {2, 3}};
        for (int[] sf : faelle) {
            System.out.printf("  Erfolge=%d, Fehler=%d  ->  P(richtig)=%.2f%n",
                    sf[0], sf[1], lib.erfolgswahrscheinlichkeit("B05", sf[0], sf[1], GAMMA, RHO));
        }
        System.out.println();

        // Aufgabenauswahl: die am wenigsten beherrschte Kategorie
        Map<String, int[]> statistik = new HashMap<>();
        statistik.put("B05", new int[]{0, 3});
        statistik.put("B01", new int[]{2, 0});
        lib.naechsteKategorie(statistik, GAMMA, RHO).ifPresent(m ->
                System.out.println("Naechste Aufgabe (schwaechste Kategorie): " + m.id() + " - " + m.name()));
    }

    private static String kuerze(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }
}