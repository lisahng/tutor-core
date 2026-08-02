package de.lmu.tutor.demo;

import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.StepEvaluator;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;

/**
 * Zeigt Schritt 4: erzeugt je Bug-Kategorie eine Aufgabe und laesst sie vom
 * Evaluator loesen (Generator und Evaluator im Zusammenspiel).
 * Ausfuehren mit:
 * <pre>  mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.GeneratorDemo  </pre>
 */
public final class GeneratorDemo {

    public static void main(String[] args) {
        AufgabenGenerator gen = new AufgabenGenerator(42); // fester Seed -> reproduzierbar
        StepEvaluator ev = new StepEvaluator();

        String[] kategorien = {"B01", "B02", "B03", "B04", "B05", "B06", "B07",
                "B08", "B09", "B10", "B11", "B12", "B13", "B14"};

        System.out.println("=== Aufgaben-Generator - Demo ===\n");
        System.out.printf("%-5s %-34s %s%n", "Kat.", "Erzeugter Ausdruck", "Referenzloesung");
        System.out.println("-".repeat(78));
        for (String k : kategorien) {
            Aufgabe auf = gen.generiere(k);
            EvaluationResult r = ev.evaluate(auf.ausdruck(), auf.kontext());
            String bel = auf.belegung().isEmpty() ? "" : "   [" + auf.belegung() + "]";
            System.out.printf("%-5s %-34s %s%s%n", k, auf.render(), r.ergebnisText(), bel);
        }
    }
}