package de.lmu.tutor.diagnose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import de.lmu.tutor.ast.Expr;
import de.lmu.tutor.ast.JType;
import de.lmu.tutor.buglib.BugLibrary;
import de.lmu.tutor.buglib.Misconception;
import de.lmu.tutor.buglib.Subtype;
import de.lmu.tutor.eval.EvaluationResult;
import de.lmu.tutor.eval.Value;
import de.lmu.tutor.gen.Aufgabe;

/**
 * Schritt 5 der Systemarchitektur: identifiziert den Fehlertyp durch Abgleich mit der Bug
 * Library. Baut auf dem {@link Vergleichsmodul} auf und geht in drei Stufen vor, von der
 * sichersten zur unsichersten Diagnose:
 *
 * <ol>
 *   <li><b>Exakte Signaturen</b> - Faelle, die sich allein am Vergleichsergebnis
 *       festmachen lassen, etwa ein richtiger Wert bei falschem Datentyp.</li>
 *   <li><b>Simulierte Fehlregeln</b> ({@link Fehlersimulator}) - reproduziert eine Regel
 *       genau den abgegebenen Wert, ist das die plausibelste Erklaerung.</li>
 *   <li><b>Zielkategorie, Strukturvermutung, unbekannt</b> - reproduziert keine Regel den
 *       Wert, gilt die Kategorie, fuer die die Aufgabe erzeugt wurde. Danach ein grober
 *       Treffer anhand des Wurzelknotens. Bleibt auch das erfolglos, wird der Fall als
 *       unbekannt markiert.</li>
 * </ol>
 *
 * <p><b>Mehrere passende Regeln.</b> Treffen mehrere Erklaerungen zu, gewinnt in dieser
 * Reihenfolge: die einfachere Erklaerung vor der zusammengesetzten, dann die
 * Zielkategorie der Aufgabe, dann die laut Bug Library haeufigere Fehlvorstellung. Der
 * erste Punkt ist wichtig, weil eine Kombination zweier Annahmen immer unwahrscheinlicher
 * ist als eine einzelne. Nur wenn keine einzelne Regel den Wert trifft, wird eine
 * Kombination gemeldet.</p>
 *
 * <p>Die IDs folgen der Nummerierung aus Anhang A.1 der Arbeit.</p>
 */
public final class Fehlerklassifikator {

    private final BugLibrary bibliothek;
    private final Vergleichsmodul vergleichsmodul = new Vergleichsmodul();
    private final Fehlersimulator simulator = new Fehlersimulator();

    public Fehlerklassifikator(BugLibrary bibliothek) {
        this.bibliothek = bibliothek;
    }

    public Diagnose diagnostiziere(Aufgabe aufgabe, EvaluationResult referenz, NutzerAntwort antwort) {
        Vergleichsergebnis vergleich = vergleichsmodul.vergleiche(referenz, antwort);
        if (vergleich.korrekt()) {
            return Diagnose.korrekteAntwort();
        }

        Optional<Diagnose> exakt = exakteSignatur(aufgabe, referenz, antwort, vergleich);
        if (exakt.isPresent()) {
            return exakt.get();
        }

        Optional<Diagnose> simuliert = ueberSimulationDiagnostizieren(aufgabe, antwort);
        if (simuliert.isPresent()) {
            return simuliert.get();
        }

        String zielId = aufgabe.kategorieId();
        Optional<Misconception> ziel = zielId == null ? Optional.empty() : bibliothek.byId(zielId);
        if (ziel.isPresent()) {
            return Diagnose.von(ziel.get(), Diagnose.Konfidenz.ZIELKATEGORIE,
                    "Kein Fehlermuster hat den abgegebenen Wert reproduziert. Die Aufgabe wurde "
                            + "jedoch gezielt fuer " + zielId + " erzeugt.");
        }

        Optional<Diagnose> vermutet = strukturVermutung(aufgabe.ausdruck());
        if (vermutet.isPresent()) {
            return vermutet.get();
        }

        return Diagnose.unbekannt("Kein passendes Fehlermuster gefunden. Kandidat fuer eine "
                + "Erweiterung der Bug Library.");
    }

    // ================================================================
    // Stufe 1: exakte Signaturen
    // ================================================================

    private Optional<Diagnose> exakteSignatur(Aufgabe aufgabe, EvaluationResult referenz,
                                              NutzerAntwort antwort, Vergleichsergebnis vergleich) {
        // B08: nicht auswertbarer Ausdruck mit einem Wert beantwortet
        if (!referenz.auswertbar() && antwort.auswertbar()) {
            Optional<Misconception> b08 = bibliothek.byId("B08");
            if (b08.isPresent()) {
                Optional<Subtype> untertyp = untertypAusGrund(b08.get(), referenz.nichtAuswertbarGrund());
                return Optional.of(Diagnose.vonMitUntertyp(b08.get(), untertyp.orElse(null),
                        Diagnose.Konfidenz.EXAKT,
                        "Der Ausdruck ist nicht auswertbar (" + referenz.nichtAuswertbarGrund()
                                + "), wurde aber mit einem Wert beantwortet."));
            }
        }

        // B09: dank Kurzschluss auswertbar, aber als nicht auswertbar markiert
        if (referenz.auswertbar() && !antwort.auswertbar() && enthaeltKurzschlussOperator(aufgabe.ausdruck())) {
            Optional<Misconception> b09 = bibliothek.byId("B09");
            if (b09.isPresent()) {
                return Optional.of(Diagnose.von(b09.get(), Diagnose.Konfidenz.EXAKT,
                        "Der Ausdruck enthaelt && oder || und ist dank Kurzschlussauswertung "
                                + "auswertbar, wurde aber als nicht auswertbar markiert."));
            }
        }

        if (referenz.auswertbar() && antwort.auswertbar()) {
            Value ref = referenz.wert();
            Value nutzer = antwort.wert();

            // B13: richtiges Zeichen, aber als String statt als char. Das zaehlt im
            // Vergleichsmodul nicht als richtiger Wert, deshalb vor der wertKorrekt-Pruefung.
            if (ref.typ() == JType.CHAR && nutzer.typ() == JType.STRING
                    && nutzer.asString().length() == 1 && nutzer.asString().charAt(0) == ref.asChar()) {
                Optional<Misconception> b13 = bibliothek.byId("B13");
                if (b13.isPresent()) {
                    return Optional.of(Diagnose.von(b13.get(), Diagnose.Konfidenz.EXAKT,
                            "Das richtige Zeichen wurde als String \"" + ref.asChar()
                                    + "\" statt als char '" + ref.asChar() + "' angegeben."));
                }
            }

            // B07: Wert stimmt, nur der Datentyp ist falsch
            if (vergleich.wertKorrekt() && !vergleich.typKorrekt()) {
                Optional<Misconception> b07 = bibliothek.byId("B07");
                if (b07.isPresent()) {
                    return Optional.of(Diagnose.von(b07.get(), Diagnose.Konfidenz.EXAKT,
                            "Der Wert stimmt (" + ref.render() + "), der Datentyp ist falsch angegeben ("
                                    + nutzer.typ().javaName() + " statt " + ref.typ().javaName() + ")."));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Subtype> untertypAusGrund(Misconception b08, String grund) {
        if (!b08.hatUntertypen() || grund == null) {
            return Optional.empty();
        }
        String g = grund.toLowerCase();
        String gesucht;
        if (g.startsWith("variable")) {
            gesucht = "B08b";
        } else if (g.contains("division durch null") || g.contains("ausserhalb der grenzen")) {
            gesucht = "B08c";
        } else if (g.contains("nicht definiert") || g.contains("nicht zulaessig")
                || g.contains("nicht unterstuetzt")) {
            gesucht = "B08a";
        } else {
            return Optional.empty();
        }
        for (Subtype s : b08.untertypen()) {
            if (s.id().equals(gesucht)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private boolean enthaeltKurzschlussOperator(Expr e) {
        if (e instanceof Expr.Bin b && (b.op().equals("&&") || b.op().equals("||"))) {
            return true;
        }
        for (Expr kind : e.children()) {
            if (enthaeltKurzschlussOperator(kind)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================
    // Stufe 2: simulierte Fehlregeln
    // ================================================================

    private Optional<Diagnose> ueberSimulationDiagnostizieren(Aufgabe aufgabe, NutzerAntwort antwort) {
        if (!antwort.auswertbar()) {
            return Optional.empty(); // Simulationen liefern stets einen Wert
        }
        List<Fehlersimulator.Treffer> passende =
                simulator.simuliereAlle(aufgabe.ausdruck(), aufgabe.kontext()).stream()
                        .filter(t -> Vergleichsmodul.werteGleich(t.wert(), antwort.wert()))
                        .toList();
        if (passende.isEmpty()) {
            return Optional.empty();
        }
        Fehlersimulator.Treffer gewaehlt = waehleWahrscheinlichsten(passende, aufgabe.kategorieId());
        List<Misconception> kategorien = nachschlagen(gewaehlt.bugIds());
        if (kategorien.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Diagnose.vonKombination(kategorien, Diagnose.Konfidenz.SIMULIERT,
                gewaehlt.erklaerung()));
    }

    private List<Misconception> nachschlagen(List<String> ids) {
        List<Misconception> gefunden = new ArrayList<>();
        for (String id : ids) {
            bibliothek.byId(id).ifPresent(gefunden::add);
        }
        return gefunden;
    }

    /**
     * Priorisierung bei mehreren passenden Regeln.
     *
     * <p>Zuerst zaehlt die Zahl der beteiligten Fehlvorstellungen: Eine einzelne Annahme
     * ist immer wahrscheinlicher als zwei gleichzeitige, eine Kombination wird also nur
     * gemeldet, wenn keine einzelne Regel den Wert trifft. Bei gleicher Zahl gewinnt die
     * Zielkategorie der Aufgabe, sonst die laut Bug Library haeufigere Fehlvorstellung.</p>
     */
    private Fehlersimulator.Treffer waehleWahrscheinlichsten(List<Fehlersimulator.Treffer> passende,
                                                             String zielKategorieId) {
        return passende.stream()
                .min(Comparator
                        .comparingInt((Fehlersimulator.Treffer t) -> t.bugIds().size())
                        .thenComparing(t -> t.bugIds().contains(zielKategorieId) ? 0 : 1)
                        .thenComparing(t -> -gewicht(t.bugId())))
                .orElse(passende.get(0));
    }

    private double gewicht(String bugId) {
        return bibliothek.byId(bugId).map(Misconception::beta).orElse(0.0);
    }

    // ================================================================
    // Stufe 3: grobe Vermutung anhand der Ausdrucksstruktur
    // ================================================================

    private Optional<Diagnose> strukturVermutung(Expr wurzel) {
        Optional<String> kandidat = switch (wurzel) {
            case Expr.Bin b -> switch (b.op()) {
                case "+", "-", "*" -> Optional.of("B01");
                case "/" -> Optional.of("B02");
                case "%" -> Optional.of("B10");
                case "==", "!=" -> Optional.of("B14");
                default -> Optional.<String>empty();
            };
            case Expr.Cast c -> Optional.of("B04");
            case Expr.Index ix -> Optional.of("B05");
            case Expr.Call c -> switch (c.methode()) {
                case "charAt" -> Optional.of("B13");
                case "equals" -> Optional.of("B14");
                default -> Optional.of("B06");
            };
            case Expr.Ternary t -> Optional.of("B11");
            case Expr.IncDec d -> Optional.of("B12");
            default -> Optional.<String>empty();
        };
        return kandidat.flatMap(bibliothek::byId)
                .map(m -> Diagnose.von(m, Diagnose.Konfidenz.VERMUTET,
                        "Grobe Vermutung anhand der Ausdrucksstruktur, ohne reproduzierten Fehlwert."));
    }
}