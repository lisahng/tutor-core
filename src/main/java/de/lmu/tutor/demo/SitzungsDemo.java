package de.lmu.tutor.demo;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.Fehlersimulator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.EvaluationStep;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.session.Antwortergebnis;
import de.lmu.tutor.session.Uebungssitzung;
import de.lmu.tutor.student.Kategoriestatistik;

/**
 * Spielt eine komplette Uebungssitzung durch und zeigt das Zusammenspiel aller
 * Komponenten: Aufgabenauswahl, Generierung, Auswertung, Diagnose, Feedback,
 * Studentenmodell und Protokoll.
 *
 * <p>Drei Faelle wechseln sich ab, weil sich das Feedback jeweils grundlegend
 * unterscheidet:</p>
 *
 * <ul>
 *   <li><b>Typischer Fehler.</b> Die Eingabe stammt aus dem {@link Fehlersimulator},
 *       entspricht also genau dem, was jemand mit einer bestimmten Fehlvorstellung
 *       rechnen wuerde. Das System kann die Kategorie benennen und gibt die drei
 *       Scaffolding-Stufen aus der Bug Library aus.</li>
 *   <li><b>Vertipper.</b> Ein Wert, den nachweislich keine Fehlregel reproduziert. Hier
 *       behauptet das System keine Fehlvorstellung, sondern gleicht den Loesungsweg ab.
 *       Nach VanLehn ist ein einzelner unerklaerbarer Wert eher ein Ausrutscher als eine
 *       systematisch falsche Regel.</li>
 *   <li><b>Richtige Antwort.</b></li>
 * </ul>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.SitzungsDemo"  </pre>
 */
public final class SitzungsDemo {

    /** Was die simulierte Lernende bei einer Aufgabe tut. */
    private enum Verhalten { TYPISCHER_FEHLER, VERTIPPER, RICHTIG }

    /** Reihum, damit in einem Durchlauf alle drei Faelle vorkommen. */
    private static final Verhalten[] ABLAUF = {
            Verhalten.TYPISCHER_FEHLER, Verhalten.VERTIPPER, Verhalten.RICHTIG,
            Verhalten.TYPISCHER_FEHLER, Verhalten.VERTIPPER, Verhalten.RICHTIG
    };

    private static final Fehlersimulator SIMULATOR = new Fehlersimulator();

    public static void main(String[] args) {
        Uebungssitzung sitzung = new Uebungssitzung(BugLibrary.loadDefault(), 2026L);

        System.out.println("Uebungssitzung - Demo");
        System.out.println("=".repeat(74));

        for (int i = 0; i < ABLAUF.length; i++) {
            Aufgabe aufgabe = sitzung.naechsteAufgabe();
            zeigeAufgabe(i + 1, aufgabe);

            if (ABLAUF[i] == Verhalten.RICHTIG) {
                antworteRichtig(sitzung);
            } else {
                antworteDreimalFalsch(sitzung, aufgabe, ABLAUF[i]);
            }
            System.out.println();
        }

        zeigeKenntnisstand(sitzung);
        zeigeProtokoll(sitzung);
    }

    // ---- Aufgabe stellen ----

    private static void zeigeAufgabe(int nummer, Aufgabe aufgabe) {
        System.out.printf("Aufgabe %d  [Zielkategorie %s]%n", nummer, aufgabe.kategorieId());
        if (!aufgabe.belegung().isBlank()) {
            System.out.println("  Gegeben:   " + aufgabe.belegung());
        }
        System.out.println("  Ausdruck:  " + aufgabe.render());
    }

    // ---- Richtige Antwort ----

    private static void antworteRichtig(Uebungssitzung sitzung) {
        EvaluationResult loesung = sitzung.musterloesung().orElseThrow();
        NutzerAntwort antwort = loesung.auswertbar()
                ? NutzerAntwort.wert(loesung.wert())
                : NutzerAntwort.nichtAuswertbar();

        System.out.println("  Verhalten: richtige Antwort");
        System.out.println("  Eingabe:   " + beschreibe(antwort));
        System.out.println("  Feedback:  " + sitzung.antworte(antwort).text());
    }

    // ---- Falsche Antwort, dreimal ----

    /** Dreimal dieselbe falsche Antwort, damit die Stufung sichtbar wird. */
    private static void antworteDreimalFalsch(Uebungssitzung sitzung, Aufgabe aufgabe,
                                              Verhalten verhalten) {
        EvaluationResult loesung = sitzung.musterloesung().orElseThrow();
        NutzerAntwort antwort = eingabeFuer(verhalten, aufgabe, loesung);

        System.out.println("  Verhalten: " + beschreibe(verhalten));
        System.out.println("  Eingabe:   " + beschreibe(antwort) + "  (dreimal)");
        System.out.println("  Loesung:   " + loesung.ergebnisText());

        for (int versuch = 1; versuch <= 3; versuch++) {
            Antwortergebnis ergebnis = sitzung.antworte(antwort);

            if (versuch == 1) {
                // Die Einordnung stammt aus der tatsaechlichen Diagnose, nicht aus der
                // Absicht der Demo. So zeigt die Ausgabe, was das System wirklich kann.
                System.out.println("  Diagnose:  "
                        + ergebnis.diagnose().misconception()
                                .map(m -> m.id() + " " + m.name()).orElse("keine")
                        + "  [" + ergebnis.diagnose().konfidenz()
                        + (ergebnis.diagnose().erklaert() ? "" : ", nur vermutet") + "]");
                System.out.println("  Modus:     " + (ergebnis.diagnose().erklaert()
                        ? "Scaffolding aus der Bug Library"
                        : "Abgleich des Loesungswegs, keine Kategorie genannt"));
            }
            System.out.printf("  Stufe %d:   %s%n",
                    ergebnis.rueckmeldung().stufe(), ergebnis.text());
        }
        zeigeZwischenschritte(loesung);
    }

    /** Die Eingabe, die zum gewuenschten Verhalten passt. */
    private static NutzerAntwort eingabeFuer(Verhalten verhalten, Aufgabe aufgabe,
                                             EvaluationResult loesung) {
        if (verhalten == Verhalten.TYPISCHER_FEHLER) {
            Optional<Fehlersimulator.Treffer> treffer = typischerFehler(aufgabe);
            if (treffer.isPresent()) {
                return NutzerAntwort.wert(treffer.get().wert());
            }
            // Keine Regel fuer diese Aufgabenform vorhanden: dann eben ein Vertipper.
        }
        return vertipper(aufgabe, loesung);
    }

    /** Der erste simulierte Fehlwert, bevorzugt zur Zielkategorie der Aufgabe. */
    private static Optional<Fehlersimulator.Treffer> typischerFehler(Aufgabe aufgabe) {
        List<Fehlersimulator.Treffer> alle =
                SIMULATOR.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext());
        return alle.stream()
                .filter(t -> t.bugId().equals(aufgabe.kategorieId()))
                .findFirst()
                .or(() -> alle.stream().findFirst());
    }

    /**
     * Ein Wert, der nachweislich weder die Loesung ist noch von einer Fehlregel erzeugt
     * wird. Nur so ist sicher, dass die Demo wirklich den Abgleich des Loesungswegs zeigt
     * und nicht zufaellig doch eine Kategorie trifft.
     */
    private static NutzerAntwort vertipper(Aufgabe aufgabe, EvaluationResult loesung) {
        Set<String> belegt = new HashSet<>();
        if (loesung.auswertbar()) {
            belegt.add(loesung.wert().render());
        }
        for (Fehlersimulator.Treffer t : SIMULATOR.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext())) {
            belegt.add(t.wert().render());
        }
        int kandidat = -1;
        while (belegt.contains(Value.ofInt(kandidat).render())) {
            kandidat--;
        }
        return NutzerAntwort.wert(Value.ofInt(kandidat));
    }

    private static void zeigeZwischenschritte(EvaluationResult loesung) {
        if (loesung.schritte().isEmpty()) {
            return;
        }
        System.out.println("  Schritte:");
        for (EvaluationStep schritt : loesung.schritte()) {
            System.out.println("    " + schritt);
        }
    }

    private static String beschreibe(NutzerAntwort antwort) {
        return antwort.auswertbar() ? antwort.wert().mitTyp() : "nicht auswertbar";
    }

    private static String beschreibe(Verhalten verhalten) {
        return switch (verhalten) {
            case TYPISCHER_FEHLER -> "typischer Fehler, laesst sich nachrechnen";
            case VERTIPPER -> "Vertipper, keine Regel erklaert den Wert";
            case RICHTIG -> "richtige Antwort";
        };
    }

    // ---- Auswertung am Ende ----

    private static void zeigeKenntnisstand(Uebungssitzung sitzung) {
        System.out.println("=".repeat(74));
        System.out.printf("Kenntnisstand nach %d Versuchen (%d richtig)%n",
                sitzung.protokoll().size(), sitzung.richtigeAntworten());

        for (Kategoriestatistik stand : sitzung.kenntnisstand()) {
            if (stand.versuche() > 0) {
                System.out.println("  " + stand);
            }
        }
        System.out.printf("  Anteil erklaerter Fehler: %.0f%%%n",
                sitzung.anteilErklaerterFehler() * 100);
        System.out.println();
    }

    private static void zeigeProtokoll(Uebungssitzung sitzung) {
        System.out.println("=".repeat(74));
        System.out.println("Systemprotokoll als CSV (Auszug)");
        System.out.println();

        String[] zeilen = sitzung.protokollAlsCsv().split("\\R");
        int anzuzeigen = Math.min(zeilen.length, 5);
        for (int i = 0; i < anzuzeigen; i++) {
            System.out.println(zeilen[i]);
        }
        if (zeilen.length > anzuzeigen) {
            System.out.printf("... und %d weitere Zeilen%n", zeilen.length - anzuzeigen);
        }
    }
}
