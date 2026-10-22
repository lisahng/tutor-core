package de.lmu.tutor.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.diagnose.NutzerAntwort;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.feedback.Feedbackgenerator;
import de.lmu.tutor.gen.AufgabenGenerator;
import de.lmu.tutor.student.Studentenmodell;

/**
 * Tests fuer die Kontrollbedingung der Evaluationsstudie.
 *
 * <p>Ein Vergleich traegt nur, wenn sich die Gruppen in <em>genau einer</em> Sache
 * unterscheiden. Der Faktor ist die Aufgabenauswahl: In der Tutorgruppe waehlt das
 * Studentenmodell nach PFA, in der Kontrollgruppe laeuft eine feste Reihenfolge reihum
 * durch alle Kategorien. Diese Klasse prueft beide Seiten davon, also dass die Auswahl
 * tatsaechlich auseinanderlaeuft und dass alles andere gleich bleibt. Die Testnamen sind
 * damit zugleich die Liste der Bedingungen, die fuer einen fairen Vergleich erfuellt sein
 * muessen.</p>
 */
class KontrollbedingungTest {

    private final BugLibrary lib = BugLibrary.loadDefault();

    private static final long SEED = 2026L;

    /** So lange, dass die feste erste Runde sicher vorbei ist. */
    private static final int LANGE_FOLGE = 40;

    private Uebungssitzung tutor() {
        return sitzung(Teilnehmer.Gruppe.TUTOR);
    }

    private Uebungssitzung kontrolle() {
        return sitzung(Teilnehmer.Gruppe.KONTROLLE);
    }

    private Uebungssitzung sitzung(Teilnehmer.Gruppe gruppe) {
        return new Uebungssitzung(lib, new Teilnehmer("P01", gruppe),
                new AufgabenGenerator(SEED),
                new Feedbackgenerator(new Random(SEED)),
                new Studentenmodell(lib),
                Clock.systemDefaultZone());
    }

    /** Eine garantiert falsche Antwort, in beiden Gruppen dieselbe. */
    private NutzerAntwort falsch() {
        return NutzerAntwort.wert(Value.ofInt(-987654));
    }

    private NutzerAntwort richtig(Uebungssitzung s) {
        EvaluationResult referenz = s.musterloesung().orElseThrow();
        return referenz.auswertbar()
                ? NutzerAntwort.wert(referenz.wert())
                : NutzerAntwort.nichtAuswertbar();
    }

    /**
     * Laesst eine Sitzung durchlaufen, in der genau eine Kategorie nie gelingt, und gibt
     * die Folge der gestellten Kategorien zurueck.
     */
    private List<String> folge(Uebungssitzung s, String schwach, int anzahl) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            String id = s.naechsteAufgabe().kategorieId();
            ids.add(id);
            if (schwach.equals(id)) {
                s.antworte(falsch());
            } else {
                s.antworte(richtig(s));
            }
        }
        return ids;
    }

    // ================================================================
    // Der eine Unterschied
    // ================================================================

    @Test
    void dieKontrollgruppeWiederholtEineSchwacheKategorieNicht() {
        List<String> ids = folge(kontrolle(), "B02", LANGE_FOLGE);

        long haeufigste = ids.stream().distinct()
                .mapToLong(id -> ids.stream().filter(id::equals).count())
                .max().orElse(0);
        long seltenste = ids.stream().distinct()
                .mapToLong(id -> ids.stream().filter(id::equals).count())
                .min().orElse(0);

        assertTrue(haeufigste - seltenste <= 1,
                "Die feste Reihenfolge muss jede Kategorie gleich oft stellen, war: " + ids);
    }

    @Test
    void dieTutorgruppeWiederholtDieSchwacheKategorieOefter() {
        long tutorZahl = folge(tutor(), "B02", LANGE_FOLGE).stream()
                .filter("B02"::equals).count();
        long kontrollZahl = folge(kontrolle(), "B02", LANGE_FOLGE).stream()
                .filter("B02"::equals).count();

        assertTrue(tutorZahl > kontrollZahl,
                "Die adaptive Auswahl muss die schwache Kategorie oefter stellen, war "
                        + tutorZahl + " gegen " + kontrollZahl);
    }

    @Test
    void dieBeidenFolgenLaufenNachDerErstenRundeAuseinander() {
        List<String> tutorFolge = folge(tutor(), "B02", LANGE_FOLGE);
        List<String> kontrollFolge = folge(kontrolle(), "B02", LANGE_FOLGE);

        assertNotEquals(tutorFolge, kontrollFolge,
                "Waeren die Folgen gleich, gaebe es keinen Faktor zu untersuchen");
    }

    @Test
    void dieFesteReihenfolgeLaeuftZyklischUndLaesstKeineKategorieAus() {
        List<String> ids = folge(kontrolle(), "B02", lib.size());

        assertEquals(lib.size(), new LinkedHashSet<>(ids).size(),
                "Die erste Runde muss jede Kategorie genau einmal enthalten, war: " + ids);
    }

    // ================================================================
    // Was absichtlich gleich bleibt
    // ================================================================

    @Test
    void dieRueckmeldungIstInBeidenGruppenWortgleich() {
        Uebungssitzung t = tutor();
        Uebungssitzung k = kontrolle();
        t.aufgabeZu("B02");
        k.aufgabeZu("B02");

        for (int versuch = 0; versuch < 3; versuch++) {
            assertEquals(t.antworte(falsch()).text(), k.antworte(falsch()).text(),
                    "Verglichen wird die Auswahl, nicht die Rueckmeldung. Versuch " + versuch);
        }
    }

    @Test
    void eineRichtigeAntwortWirdInBeidenGruppenGleichBestaetigt() {
        Uebungssitzung t = tutor();
        Uebungssitzung k = kontrolle();
        t.aufgabeZu("B01");
        k.aufgabeZu("B01");

        assertEquals(t.antworte(richtig(t)).text(), k.antworte(richtig(k)).text());
    }

    @Test
    void beideGruppenDuerfenGleichOftVersuchen() {
        Uebungssitzung t = tutor();
        Uebungssitzung k = kontrolle();
        t.aufgabeZu("B02");
        k.aufgabeZu("B02");

        for (int i = 0; i < 3; i++) {
            t.antworte(falsch());
            k.antworte(falsch());
        }
        assertEquals(t.versucheAnAktuellerAufgabe(), k.versucheAnAktuellerAufgabe());
        assertEquals(t.protokoll().size(), k.protokoll().size());
    }

    @Test
    void dieDiagnoseLaeuftInBeidenGruppenGleich() {
        Uebungssitzung t = tutor();
        Uebungssitzung k = kontrolle();
        t.aufgabeZu("B02");
        k.aufgabeZu("B02");
        t.antworte(falsch());
        k.antworte(falsch());

        Protokolleintrag tz = t.protokoll().get(0);
        Protokolleintrag kz = k.protokoll().get(0);

        assertEquals(tz.diagnose(), kz.diagnose());
        assertEquals(tz.vermutung(), kz.vermutung());
        assertEquals(tz.erklaert(), kz.erklaert());
        assertEquals(tz.konfidenz(), kz.konfidenz());
        assertEquals(tz.feedbackStufe(), kz.feedbackStufe());
    }

    @Test
    void dieGruppeStehtInJederZeile() {
        Uebungssitzung k = kontrolle();
        k.naechsteAufgabe();
        k.antworte(falsch());

        assertTrue(k.protokoll().stream()
                .allMatch(z -> z.gruppe() == Teilnehmer.Gruppe.KONTROLLE));
    }

    // ================================================================
    // Zuteilung ueber das Pseudonym
    // ================================================================

    @Test
    void ungeradeNummernKommenInDieTutorgruppe() {
        assertEquals(Teilnehmer.Gruppe.TUTOR, Teilnehmer.ausKennung("P01").gruppe());
        assertEquals(Teilnehmer.Gruppe.TUTOR, Teilnehmer.ausKennung("P07").gruppe());
        assertEquals(Teilnehmer.Gruppe.TUTOR, Teilnehmer.ausKennung("P23").gruppe());
    }

    @Test
    void geradeNummernKommenInDieKontrollgruppe() {
        assertEquals(Teilnehmer.Gruppe.KONTROLLE, Teilnehmer.ausKennung("P02").gruppe());
        assertEquals(Teilnehmer.Gruppe.KONTROLLE, Teilnehmer.ausKennung("P10").gruppe());
        assertEquals(Teilnehmer.Gruppe.KONTROLLE, Teilnehmer.ausKennung("P24").gruppe());
    }

    @Test
    void fuehrendeNullenUndLeerzeichenAendernDieZuteilungNicht() {
        assertEquals(Teilnehmer.Gruppe.TUTOR, Teilnehmer.ausKennung("  P007  ").gruppe());
        assertEquals("P007", Teilnehmer.ausKennung("  P007  ").id());
    }

    @Test
    void dieZuteilungWechseltUndHaeltDieGruppenGleichGross() {
        long tutorZahl = 0;
        for (int nummer = 1; nummer <= 20; nummer++) {
            if (Teilnehmer.ausKennung("P" + nummer).gruppe() == Teilnehmer.Gruppe.TUTOR) {
                tutorZahl++;
            }
        }
        assertEquals(10, tutorZahl, "Bei zwanzig Kennungen muessen zehn je Gruppe entstehen");
    }

    @Test
    void eineKennungOhneNummerWirdZurueckgewiesen() {
        try {
            Teilnehmer.ausKennung("Pilot");
            throw new AssertionError("Ohne Nummer gibt es keine Gruppe, das muss auffallen");
        } catch (IllegalArgumentException erwartet) {
            assertTrue(erwartet.getMessage().contains("Nummer"));
        }
    }
}
