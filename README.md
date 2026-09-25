# tutor-core

Kernlogik eines intelligenten Tutorsystems für die schrittweise Auswertung von
Java-Ausdrücken. Entstanden im Rahmen einer Zulassungsarbeit an der LMU München
(FQ2: Entwurf und technische Umsetzung). Ohne Benutzeroberfläche.

## Architektur

| Package | Komponente |
| `ast` | Ausdrücke als Baum, inklusive Klammersetzung |
| `buglib` | 14 Fehlerkategorien mit je drei Feedback-Stufen |
| `eval` | Auswertung zu Wert, Datentyp und Zwischenschritten |
| `gen` | Generator für typkonforme Aufgaben je Kategorie |
| `diagnose` | Vergleichsmodul und Fehlerklassifikator |
| `feedback` | Auswahl der Scaffolding-Stufe |
| `student` | Fehlerhistorie und Aufgabenauswahl via PFA |
| `session` | Fassade über den gesamten Ablauf, Protokollierung |

Die Kernlogik ist abhängigkeitsfrei; JSON liest ein eigener Minimal-Parser.
JUnit 5 nur im Test-Scope. Erfordert JDK 21.

## Verwendung

```java
Uebungssitzung sitzung = new Uebungssitzung(BugLibrary.loadDefault());

Aufgabe aufgabe = sitzung.naechsteAufgabe();
Antwortergebnis ergebnis = sitzung.antworte(NutzerAntwort.wert(eingabe));
```

`naechsteAufgabe()` wählt die Kategorie mit der niedrigsten geschätzten
Erfolgswahrscheinlichkeit, erzeugt dazu einen Ausdruck und berechnet dessen
Lösung vorab. `antworte(…)` vergleicht, diagnostiziert, erzeugt Feedback,
aktualisiert das Studentenmodell und schreibt einen Protokolleintrag.

Eine Aufgabe bleibt gestellt, bis `naechsteAufgabe()` erneut aufgerufen wird;
weitere Versuche erhöhen die Scaffolding-Stufe. Eine Sitzung ist nicht
threadsicher und gehört pro Nutzer in die HTTP-Session.

## Bauen

```bash
mvn clean test
mvn compile exec:java "-Dexec.mainClass=de.lmu.tutor.demo.SitzungsDemo"
```

Weitere Demos unter `de.lmu.tutor.demo`: `BugLibraryDemo`, `AstDemo`,
`EvaluatorDemo`, `GeneratorDemo`, `DiagnoseDemo`, `FeedbackDemo`,
`StudentenmodellDemo`. Jede zeigt genau eine Komponente.

Die `pom.xml` begrenzt den Heap der Test-JVM und pinnt `exec-maven-plugin` auf
3.1.1; 3.2.0 findet Ressourcen im Classpath nicht zuverlässig.

## Fehlerdiagnose

Der Klassifikator arbeitet dreistufig, von der sichersten zur unsichersten
Deutung. Die Konfidenz wird mitprotokolliert, da sie für den Abgleich mit
Think-Aloud-Protokollen benötigt wird.

1. **Signatur** — aus dem Vergleichsergebnis allein ableitbar, etwa richtiger
   Wert bei falschem Datentyp.
2. **Simulation** — eine gestörte Fassung der korrekten Regel wird auf den
   Ausdruck angewendet (Perturbationsmodell, Brown & VanLehn 1980).
   Reproduziert sie den eingegebenen Wert exakt, gilt sie als Erklärung.
3. **Rückfall** — sonst die Zielkategorie der Aufgabe, danach ein
   Strukturtreffer am Wurzelknoten, zuletzt `UNBEKANNT`.

Die Fehlerhistorie wird unter der diagnostizierten Kategorie geführt, nicht
unter der Zielkategorie der Aufgabe.

## Bekannte Einschränkungen

- `gamma` und `rho` der PFA sind gesetzt, nicht aus Daten geschätzt. Ohne
  kategorienspezifisches `beta` startet jede Kategorie bei P = 0,5; die Auswahl
  folgt damit allein der Fehlerhistorie.
- Die IDs in `bug-library.json` decken sich nicht durchgängig mit Anhang A.1 der
  Arbeit. Maßgeblich ist die JSON.
- Objekterzeugung (`new`) fehlt im AST, daher ist Klausuraufgabe 4c nicht
  abgedeckt. Statische Aufrufe (`Double.parseDouble`) sind enthalten.