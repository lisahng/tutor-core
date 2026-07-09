# tutor-core — Schritt 1: Bug Library

Kernmodul des intelligenten Tutorsystems für Java-Ausdrücke (Zulassungsarbeit, FQ2).
Dieser Stand implementiert **Schritt 1**: die Bug Library als Daten.

## Was ist enthalten

```
tutor-core/
├── pom.xml
├── src/main/resources/
│   └── bug-library.json         # die 14 Fehlerkategorien B01–B14 (Datengrundlage)
├── src/main/java/de/lmu/tutor/
│   ├── buglib/
│   │   ├── Difficulty.java       # Schwierigkeitsgrad (LEICHT/MITTEL/SCHWER)
│   │   ├── Subtype.java          # Untertyp einer Kategorie (z. B. B05a)
│   │   ├── Misconception.java    # ein Bug-Library-Eintrag
│   │   ├── BugLibrary.java       # Loader, Abfragen, Gewichtungsformel
│   │   └── json/MiniJson.java    # kleiner, abhängigkeitsfreier JSON-Parser
│   └── demo/
│       └── BugLibraryDemo.java   # ausführbare Demo (main)
└── src/test/java/de/lmu/tutor/buglib/
    └── BugLibraryTest.java        # JUnit-5-Tests
```

## Bauen und ausführen (Maven)

```bash
# Tests ausführen
mvn test

# Demo ausführen
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.BugLibraryDemo
```

Voraussetzung: JDK 17+ und Maven. Beim ersten Lauf lädt Maven JUnit herunter
(nur für die Tests; die Kernlogik selbst ist bewusst **abhängigkeitsfrei**).

## Ohne Maven ausführen (nur JDK)

```bash
# aus dem Ordner tutor-core/
javac -d target/classes $(find src/main/java -name "*.java")
cp src/main/resources/bug-library.json target/classes/
java -cp target/classes de.lmu.tutor.demo.BugLibraryDemo
```

## Die Bug Library erweitern oder ändern

Alles steht in `src/main/resources/bug-library.json`. Ein neuer Eintrag braucht:
`id`, `name`, `beschreibung`, `beispiel`, `typischerFehler`, `schwierigkeit`
(LEICHT/MITTEL/SCHWER), `basisgewicht` (ganze Zahl > 0), `konzept`, `feedback`
(mindestens 3 Stufen, allgemein → konkret) und optional `untertypen`.
Kein Java-Code muss angefasst werden.

## Aufgabengewichtung

`BugLibrary.auswahlGewicht(id, fehlerquote, alpha)` berechnet
`w(K,s) = b(K) · (1 + α · f(K,s))` — Basisgewicht aus der Bug Library,
verstärkt durch die individuelle Fehlerhäufigkeit.

## Später auf Jackson umstellen (optional)

Der `MiniJson`-Parser hält das Projekt hier abhängigkeitsfrei und sofort
lauffähig. In einer vollen Umgebung kann `BugLibrary.parse(...)` durch Jackson
ersetzt werden: `jackson-databind` als Dependency in die `pom.xml`, dann in
`BugLibrary` einen `ObjectMapper` auf `Misconception`/`Subtype` mappen. Die
öffentliche API (`loadDefault`, `all`, `byId`, `auswahlGewicht`) bleibt gleich.
```

## Nächste Schritte (Ausblick)

- **Schritt 2:** AST-Knotentypen und Schritt-Evaluator (mit Unit-Tests, abgesichert
  über die Klausur-Musterlösungen).
- **Schritt 3:** Zufallsgenerator für typkonforme Ausdrücke.
- **Schritt 4–6:** Diagnose/Feedback, Studentenmodell, Frontend.
