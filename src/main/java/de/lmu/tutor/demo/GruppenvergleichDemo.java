package de.lmu.tutor.demo;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.Fehlersimulator;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.gen.Aufgabe;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.session.Teilnehmer;
import de.lmu.tutor.session.Uebungssitzung;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Stellt die beiden Bedingungen der Evaluationsstudie nebeneinander.
 *
 * <p>Zwei Personen mit genau demselben Kenntnisstand ueben dieselbe Zeit. Beide beherrschen
 * alles ausser einer Sache, naemlich der ganzzahligen Division (B02). Die eine uebt mit dem
 * vollen System, bei dem das Studentenmodell die naechste Kategorie nach PFA waehlt, die
 * andere mit einer festen Reihenfolge reihum durch alle Kategorien.</p>
 *
 * <p><b>Worauf zu achten ist.</b> Die Rueckmeldung ist in beiden Gruppen dieselbe, die
 * Zahl der Versuche ebenfalls, und die Diagnose laeuft in beiden. Unterschiedlich ist
 * allein, <em>welche</em> Aufgabe als naechstes kommt. Die Zaehlung am Ende zeigt, dass die
 * Tutorgruppe ihre Zeit auf die schwache Kategorie verlagert, waehrend die Kontrollgruppe
 * jede Kategorie gleich oft bearbeitet. Genau das ist der untersuchte Faktor.</p>
 *
 * <p>Ausfuehren mit:
 * <pre>  mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.GruppenvergleichDemo"  </pre>
 */
public final class GruppenvergleichDemo {

    private static final BugLibrary LIB = BugLibrary.loadDefault();
    private static final Fehlersimulator SIMULATOR = new Fehlersimulator();

    private static final long SEED = 2026L;

    /** So viele Aufgaben bearbeitet jede der beiden Personen. */
    private static final int ANZAHL_AUFGABEN = 20;

    /** Diese eine Kategorie beherrscht die simulierte Person nicht. */
    private static final String SCHWACHE_KATEGORIE = "B02";

    public static void main(String[] args) {
        System.out.println("Gruppenvergleich - Demo");
        System.out.println("=".repeat(78));
        System.out.println("Zwei Personen, gleicher Kenntnisstand, gleiche Rueckmeldung.");
        System.out.println("Beide koennen alles ausser " + SCHWACHE_KATEGORIE
                + " (" + nameVon(SCHWACHE_KATEGORIE) + ").");
        System.out.println("Unterschiedlich ist allein, welche Aufgabe als naechstes kommt.");
        System.out.println();

        List<String> tutorFolge = uebe(Teilnehmer.Gruppe.TUTOR, "P01");
        List<String> kontrollFolge = uebe(Teilnehmer.Gruppe.KONTROLLE, "P02");

        zeigeFolge("Tutorgruppe, adaptiv nach PFA", tutorFolge);
        zeigeFolge("Kontrollgruppe, feste Reihenfolge", kontrollFolge);

        zeigeVerteilung(tutorFolge, kontrollFolge);
        zeigeRueckmeldung();
    }

    // ---- Ablauf ----

    /**
     * Laesst eine simulierte Person ueben und gibt die Folge der gestellten Kategorien
     * zurueck.
     *
     * <p>Die Person antwortet bei der schwachen Kategorie dreimal falsch und sonst sofort
     * richtig. Sie lernt in dieser Simulation nichts dazu, denn sonst wuerde der
     * Unterschied zwischen den Bedingungen von der angenommenen Lernrate abhaengen und
     * nicht mehr von der Auswahlregel.</p>
     */
    private static List<String> uebe(Teilnehmer.Gruppe gruppe, String kennung) {
        Uebungssitzung sitzung = sitzung(kennung, gruppe);
        List<String> folge = new ArrayList<>();

        for (int i = 0; i < ANZAHL_AUFGABEN; i++) {
            Aufgabe aufgabe = sitzung.naechsteAufgabe();
            folge.add(aufgabe.kategorieId());

            if (SCHWACHE_KATEGORIE.equals(aufgabe.kategorieId())) {
                NutzerAntwort falsch = typischerFehler(aufgabe);
                for (int versuch = 0; versuch < 3; versuch++) {
                    sitzung.antworte(falsch);
                }
            } else {
                sitzung.antworte(loesung(sitzung));
            }
        }
        return folge;
    }

    private static void zeigeFolge(String titel, List<String> folge) {
        System.out.println(titel);
        System.out.print("  ");
        for (int i = 0; i < folge.size(); i++) {
            System.out.print(folge.get(i));
            if (i < folge.size() - 1) {
                System.out.print(i % 10 == 9 ? "\n  " : " ");
            }
        }
        System.out.println();
        System.out.println();
    }

    /** Wie oft jede Kategorie in den beiden Bedingungen drankam. */
    private static void zeigeVerteilung(List<String> tutorFolge, List<String> kontrollFolge) {
        System.out.println("=".repeat(78));
        System.out.printf("Verteilung der %d Aufgaben%n", ANZAHL_AUFGABEN);
        System.out.println();
        System.out.printf("  %-6s %-38s %8s %8s%n", "ID", "Kategorie", "Tutor", "Kontrolle");

        Map<String, Integer> tutorZahl = zaehle(tutorFolge);
        Map<String, Integer> kontrollZahl = zaehle(kontrollFolge);

        LIB.all().forEach(m -> {
            int t = tutorZahl.getOrDefault(m.id(), 0);
            int k = kontrollZahl.getOrDefault(m.id(), 0);
            String marke = m.id().equals(SCHWACHE_KATEGORIE) ? "  <-- die schwache Kategorie" : "";
            System.out.printf("  %-6s %-38s %8d %8d%s%n", m.id(), kuerze(m.name()), t, k, marke);
        });

        System.out.println();
        System.out.printf("  Aufgaben zu %s: Tutorgruppe %d, Kontrollgruppe %d%n",
                SCHWACHE_KATEGORIE,
                tutorZahl.getOrDefault(SCHWACHE_KATEGORIE, 0),
                kontrollZahl.getOrDefault(SCHWACHE_KATEGORIE, 0));
        System.out.println("  Verschiedene Kategorien: Tutorgruppe " + tutorZahl.size()
                + ", Kontrollgruppe " + kontrollZahl.size());
        System.out.println();
    }

    /**
     * Zeigt, dass die Rueckmeldung in beiden Gruppen wortgleich ist.
     *
     * <p>Verglichen wird die Aufgabenauswahl. Waere auch die Rueckmeldung verschieden,
     * gaebe es mehr als einen Unterschied zwischen den Gruppen, und ein Unterschied im
     * Ergebnis liesse sich keiner der beiden Sachen zuordnen.</p>
     */
    private static void zeigeRueckmeldung() {
        System.out.println("=".repeat(78));
        System.out.println("Die Rueckmeldung zur selben Aufgabe und zum selben Fehler");
        System.out.println();

        Uebungssitzung tutor = sitzung("P01", Teilnehmer.Gruppe.TUTOR);
        Uebungssitzung kontrolle = sitzung("P02", Teilnehmer.Gruppe.KONTROLLE);

        Aufgabe aufgabe = tutor.aufgabeZu(SCHWACHE_KATEGORIE);
        kontrolle.aufgabeZu(SCHWACHE_KATEGORIE);

        System.out.println("  Ausdruck: " + aufgabe.render());
        NutzerAntwort falsch = typischerFehler(aufgabe);

        for (int versuch = 0; versuch < 3; versuch++) {
            String tutorText = tutor.antworte(falsch).text();
            String kontrollText = kontrolle.antworte(falsch).text();
            System.out.printf("  Stufe %d identisch: %s%n", versuch,
                    tutorText.equals(kontrollText));
            System.out.println("    " + tutorText);
        }
        System.out.println();
        System.out.println("  Die Diagnose laeuft in beiden Gruppen und steht in beiden");
        System.out.println("  Protokollen. Unterschiedlich ist allein die Auswahl.");
    }

    // ---- Hilfsmittel ----

    /**
     * Beide Sitzungen bekommen denselben Seed. Die Ausdruecke einer Kategorie entstehen
     * damit nach demselben Bauplan, und niemand bekommt leichtere oder schwerere Aufgaben.
     */
    private static Uebungssitzung sitzung(String id, Teilnehmer.Gruppe gruppe) {
        return new Uebungssitzung(LIB, new Teilnehmer(id, gruppe),
                new AufgabenGenerator(SEED),
                new Feedbackgenerator(new Random(SEED)),
                new Studentenmodell(LIB),
                Clock.systemDefaultZone());
    }

    private static Map<String, Integer> zaehle(List<String> folge) {
        Map<String, Integer> zahl = new LinkedHashMap<>();
        folge.forEach(id -> zahl.merge(id, 1, Integer::sum));
        return zahl;
    }

    private static String nameVon(String id) {
        return LIB.byId(id).map(m -> m.name()).orElse(id);
    }

    private static String kuerze(String text) {
        return text.length() <= 38 ? text : text.substring(0, 35) + "...";
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
}
