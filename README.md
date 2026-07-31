# tutor-core — Schritt 1: Bug Library

Kernmodul des intelligenten Tutorsystems für Java-Ausdrücke (Zulassungsarbeit, FQ2).
Dieser Stand implementiert **Schritt 1 und Schritt 2**

## Bauen und ausführen (Maven)

```bash
# Tests ausführen
mvn test

# Demo ausführen
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.BugLibraryDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.AstDemo
```

Voraussetzung: JDK 17+ und Maven. Beim ersten Lauf lädt Maven JUnit herunter
(nur für die Tests; die Kernlogik selbst ist bewusst **abhängigkeitsfrei**).

## Später auf Jackson umstellen (optional)

Der `MiniJson`-Parser hält das Projekt hier abhängigkeitsfrei und sofort
lauffähig. In einer vollen Umgebung kann `BugLibrary.parse(...)` durch Jackson
ersetzt werden: `jackson-databind` als Dependency in die `pom.xml`, dann in
`BugLibrary` einen `ObjectMapper` auf `Misconception`/`Subtype` mappen. Die
öffentliche API (`loadDefault`, `all`, `byId`, `auswahlGewicht`) bleibt gleich.

## Nächste Schritte (Ausblick)

- **Schritt 3:** Zufallsgenerator für typkonforme Ausdrücke.
- **Schritt 4–6:** Diagnose/Feedback, Studentenmodell, Frontend.
