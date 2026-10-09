package de.lmu.tutor.demo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.Fehlersimulator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.session.Antwortergebnis;
import de.lmu.tutor.session.Teilnehmer;
import de.lmu.tutor.session.Uebungssitzung;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Stellt die beiden Bedingungen der Evaluationsstudie nebeneinander.
 *
 * <p>Zwei Personen bearbeiten dieselben Aufgaben und machen dieselben Fehler. Die eine
 * uebt mit dem vollen System, die andere mit der Kontrollbedingung. Die Ausgabe zeigt
 * beides untereinander, so dass der Unterschied unmittelbar sichtbar wird.</p>
 *
 * <p><b>Worauf zu achten ist.</b> Die Aufgaben sind identisch, die Zahl der Versuche ist
 * identisch, und eine richtige Antwort wird gleich bestaetigt. Unterschiedlich ist allein
 * die Rueckmeldung auf einen Fehler. Genau das ist die Voraussetzung dafuer, dass sich ein
 * Unterschied im Ergebnis auf das diagnostische Feedback zurueckfuehren laesst.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.GruppenvergleichDemo"  </pre>
 */
public final class GruppenvergleichDemo {

    private static final BugLibrary LIB = BugLibrary.loadDefault();
    private static final Fehlersimulator SIMULATOR = new Fehlersimulator();

    private static final long SEED = 2026L;
    private static final int ANZAHL_AUFGABEN = 3;

    public static void main(String[] args) {
        Uebungssitzung tutor = sitzung("P01", Teilnehmer.Gruppe.TUTOR);
        Uebungssitzung kontrolle = sitzung("P02", Teilnehmer.Gruppe.KONTROLLE);

        System.out.println("Gruppenvergleich - Demo");
        System.out.println("=".repeat(78));
        System.out.println("Zwei Personen, dieselben Aufgaben, dieselben Fehler.");
        System.out.println("Unterschiedlich ist allein die Rueckmeldung auf einen Fehler.");
        System.out.println();

        for (int nummer = 1; nummer <= ANZAHL_AUFGABEN; nummer++) {
            Aufgabe aufgabe = tutor.naechsteAufgabe();
            Aufgabe gleicheAufgabe = kontrolle.naechsteAufgabe();

            System.out.printf("Aufgabe %d  [%s]%n", nummer, aufgabe.kategorieId());
            if (!aufgabe.belegung().isBlank()) {
                System.out.println("  Gegeben:   " + aufgabe.belegung());
            }
            System.out.println("  Ausdruck:  " + aufgabe.render());
            System.out.println("  Identisch in beiden Gruppen: "
                    + aufgabe.render().equals(gleicheAufgabe.render()));
            System.out.println("  Loesung:   " + tutor.musterloesung().orElseThrow().ergebnisText());

            NutzerAntwort eingabe = typischerFehler(aufgabe);
            System.out.println("  Eingabe:   " + beschreibe(eingabe) + "  (dreimal)");
            System.out.println();

            List<String> tutorTexte = dreimalAntworten(tutor, eingabe);
            List<String> kontrollTexte = dreimalAntworten(kontrolle, eingabe);

            zeigeBlock("Tutorgruppe", tutorTexte);
            zeigeBlock("Kontrollgruppe", kontrollTexte);
            System.out.println();
        }

        zeigeRichtigeAntwort(tutor, kontrolle);
        zeigeProtokoll(tutor, kontrolle);
    }

    // ---- Ablauf ----

    private static List<String> dreimalAntworten(Uebungssitzung sitzung, NutzerAntwort eingabe) {
        List<String> texte = new ArrayList<>();
        for (int versuch = 0; versuch < 3; versuch++) {
            Antwortergebnis ergebnis = sitzung.antworte(eingabe);
            texte.add(String.format("Stufe %d: %s", ergebnis.rueckmeldung().stufe(), ergebnis.text()));
        }
        return texte;
    }

    private static void zeigeBlock(String titel, List<String> texte) {
        System.out.println("  " + titel);
        for (String zeile : texte) {
            System.out.println("    " + zeile);
        }
    }

    /**
     * Zeigt, dass eine richtige Antwort in beiden Gruppen gleich bestaetigt wird.
     *
     * <p>Verglichen wird das Feedback auf Fehler. Waere auch das Lob verschieden, gaebe es
     * mehr als einen Unterschied zwischen den Gruppen.</p>
     */
    private static void zeigeRichtigeAntwort(Uebungssitzung tutor, Uebungssitzung kontrolle) {
        System.out.println("=".repeat(78));
        System.out.println("Eine richtige Antwort");
        System.out.println();

        tutor.aufgabeZu("B01");
        kontrolle.aufgabeZu("B01");

        String tutorText = tutor.antworte(loesung(tutor)).text();
        String kontrollText = kontrolle.antworte(loesung(kontrolle)).text();

        System.out.println("  Tutorgruppe:    " + tutorText);
        System.out.println("  Kontrollgruppe: " + kontrollText);
        System.out.println("  Identisch: " + tutorText.equals(kontrollText));
        System.out.println();
    }

    private static void zeigeProtokoll(Uebungssitzung tutor, Uebungssitzung kontrolle) {
        System.out.println("=".repeat(78));
        System.out.println("Was im Protokoll steht");
        System.out.println();
        System.out.println("  Die Diagnose laeuft in beiden Gruppen und wird in beiden");
        System.out.println("  protokolliert. Nur angezeigt wird sie der Kontrollgruppe nicht.");
        System.out.println("  So bleiben die Logdaten vergleichbar.");
        System.out.println();

        System.out.printf("  %-16s %-10s %-10s %s%n", "Gruppe", "Diagnose", "Vermutung", "Stufe");
        zeigeErsteZeile("Tutorgruppe", tutor);
        zeigeErsteZeile("Kontrollgruppe", kontrolle);
    }

    private static void zeigeErsteZeile(String titel, Uebungssitzung sitzung) {
        var zeile = sitzung.protokoll().get(0);
        System.out.printf("  %-16s %-10s %-10s %d%n",
                titel,
                zeile.diagnose().isBlank() ? "-" : zeile.diagnose(),
                zeile.vermutung().isBlank() ? "-" : zeile.vermutung(),
                zeile.feedbackStufe());
    }

    // ---- Hilfsmittel ----

    /**
     * Beide Sitzungen bekommen denselben Seed, damit sie dieselben Aufgaben erzeugen.
     * Unterschiedliche Aufgaben waeren ein zweiter Unterschied zwischen den Gruppen.
     */
    private static Uebungssitzung sitzung(String id, Teilnehmer.Gruppe gruppe) {
        return new Uebungssitzung(LIB, new Teilnehmer(id, gruppe),
                new AufgabenGenerator(SEED),
                new Feedbackgenerator(new Random(SEED)),
                new Studentenmodell(LIB),
                Clock.systemDefaultZone());
    }

    /** Ein Wert, den jemand mit einer typischen Fehlvorstellung berechnen wuerde. */
    private static NutzerAntwort typischerFehler(Aufgabe aufgabe) {
        return SIMULATOR.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext()).stream()
                .filter(t -> t.bugId().equals(aufgabe.kategorieId()))
                .findFirst()
                .or(() -> SIMULATOR.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext())
                        .stream().findFirst())
                .map(t -> NutzerAntwort.wert(t.wert()))
                .orElseGet(() -> NutzerAntwort.wert(Value.ofInt(-1)));
    }

    private static NutzerAntwort loesung(Uebungssitzung sitzung) {
        var referenz = sitzung.musterloesung().orElseThrow();
        return referenz.auswertbar()
                ? NutzerAntwort.wert(referenz.wert())
                : NutzerAntwort.nichtAuswertbar();
    }

    private static String beschreibe(NutzerAntwort antwort) {
        return antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar";
    }
}
