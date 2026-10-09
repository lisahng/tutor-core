# tutor-core

Intelligentes Tutorsystem zur automatisierten Auswertung von Java-Ausdrücken.

Entstanden im Rahmen einer Zulassungsarbeit an der LMU München, Lehramt Gymnasium
Informatik, betreut von Prof. Dr. Sven Strickroth. Das System stellt Aufgaben zur
Auswertung von Java-Ausdrücken, erkennt hinter einer falschen Antwort die zugrunde
liegende Fehlvorstellung und gibt dazu gestufte Rückmeldung.

## Was das System kann

Eine Aufgabe sieht aus wie in der Klausur. Gegeben ist eine Variablenbelegung und ein
Ausdruck, gefragt sind Typ und Wert.

```
Gegeben:  x = 4
Ausdruck: 25 / 6 + 1.5
Typ: ______   Wert: ______
```

Wer `double` und `5.666` einträgt, hat die Integer-Division nicht angewendet. Das System
erkennt das nicht daran, dass die Antwort falsch ist, sondern daran, dass sich genau dieser
Wert aus einer bekannten Fehlvorstellung erzeugen lässt. Die Rückmeldung spricht dann diese
Fehlvorstellung an und wird mit jedem Versuch konkreter.

Lässt sich eine Eingabe durch keine bekannte Fehlregel erklären, behauptet das System
nichts. Es vergleicht stattdessen die Lösungswege und bittet zunächst, auf einen Vertipper
zu prüfen. Dieser Fall wird gesondert protokolliert und ist ein Kandidat für eine neue
Kategorie der Bug Library.

## Aufbau

```
de.lmu.tutor
├── ast         Ausdrucksbaum als versiegelte Schnittstelle, Typen, Baukasten
├── eval        Schrittweiser Auswerter mit Protokoll der Zwischenschritte
├── buglib      Bug Library, vierzehn Fehlvorstellungen mit je drei Feedback-Stufen
├── gen         Aufgabengenerator, erzeugt typkonforme Ausdrücke je Kategorie
├── diagnose    Vergleich, Fehlersimulation und Klassifikation
├── feedback    Auswahl und Formulierung der Rückmeldung
├── student     Studentenmodell mit PFA und Wiederholungssperre
├── session     Fassade einer Übungssitzung samt Systemprotokoll
├── web         Weboberfläche (Spring Boot), das einzige Paket mit Abhängigkeiten
└── demo        Ausführbare Beispiele zu jedem Schritt
```

Die Kernlogik ist bewusst abhängigkeitsfrei. Spring kommt ausschließlich im Paket `web`
vor, das nichts weiter tut, als Eingaben hereinzureichen und Ergebnisse anzuzeigen. Alles
Inhaltliche lässt sich dadurch ohne Webserver testen.

## Loslegen

Voraussetzung ist JDK 21 und Maven.

```bash
mvn test                 # alle Tests
mvn spring-boot:run      # Weboberfläche auf http://localhost:8080
```

Die Weboberfläche läuft ohne Datenbank und schreibt die Protokolle als CSV nach
`protokolle/`. Für den Betrieb am Lehrstuhl gibt es ein Docker-Abbild samt MariaDB, siehe
unten.

### Die Demos

Jeder Entwicklungsschritt hat ein ausführbares Beispiel. Sie sind der schnellste Weg, um zu
sehen, was eine Komponente tut.

```bash
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.BugLibraryDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.AstDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.EvaluatorDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.GeneratorDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.DiagnoseDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.FeedbackDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.StudentenmodellDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.SitzungsDemo
mvn compile exec:java -Dexec.mainClass=de.lmu.tutor.demo.GruppenvergleichDemo
```

`GruppenvergleichDemo` stellt die beiden Bedingungen der Evaluationsstudie nebeneinander
und zeigt, dass sich die Gruppen in genau einer Sache unterscheiden.

## Betrieb mit Docker und MariaDB

Einmalig `.env.beispiel` nach `.env` kopieren und drei Werte eintragen, dann

```bash
docker compose up -d --build
```

Startet zwei Dienste, die Oberfläche und MariaDB, und legt die Tabellen beim ersten Start
an. Betreibt der Lehrstuhl die Datenbank bereits, entfällt der Dienst `datenbank` und
`TUTOR_DB_URL` zeigt dorthin.

Die Daten werden dreifach gehalten: in der Datenbank, als CSV auf einem eingehängten
Verzeichnis und als Download auf der Abschlussseite. In die Datenbank wird ausschließlich
angehängt, und jede Sitzung bekommt eine eigene Kennung, damit sich zwei Sitzungen unter
demselben Pseudonym nicht überschreiben.

Für die Auswertung:

```
/export/protokoll.csv?schluessel=...
/export/bewertungen.csv?schluessel=...
```

Die Übungsseiten sind bewusst ohne Anmeldung erreichbar. Der Export ist das nicht, dort
liegen die Daten aller Teilnehmenden beisammen.

## Einstellungen

Alles in `src/main/resources/application.properties`, wirksam nach einem Neustart.

| Eigenschaft | Standard | Bedeutung |
|---|---|---|
| `tutor.aufgaben` | 20 | Höchstzahl der Aufgaben je Sitzung |
| `tutor.arbeitszeit-minuten` | 25 | Zeitbudget der Arbeitsphase |
| `tutor.versuche` | 3 | Versuche je Aufgabe, bevor die Lösung erscheint |
| `tutor.seed` | 2026 | Startwert des Generators, für alle Teilnehmenden derselbe |
| `tutor.protokoll-ordner` | `protokolle` | wohin die CSV-Dateien gehen |
| `tutor.csv-schreiben` | `true` | zweite Kopie neben der Datenbank |
| `tutor.export-schluessel` | leer | schützt die Export-Adressen |

## Entwurfsentscheidungen

Die ausführliche Begründung steht jeweils im Javadoc der betroffenen Klasse. Die
wichtigsten in Kürze:

**Scaffolding gilt innerhalb einer Aufgabe, nicht über die Sitzung.** Bei jeder neuen
Aufgabe beginnt die Rückmeldung wieder allgemein. Die Hilfe richtet sich nach der
Schwierigkeit der konkreten Aufgabe, nicht nach der Dauer der Sitzung (Wood, Bruner und
Ross 1976).

**Keine Kategorie ohne Erklärung.** Erklärt keine Fehlregel den eingegebenen Wert, nennt das
System keine Fehlvorstellung, sondern vergleicht Lösungswege. Ein Rechenausrutscher ist
keine Fehlvorstellung, und eine geratene Kategorie würde die Auswertung verfälschen
(Brown und VanLehn 1980).

**Fehlerkombinationen nur, wenn keine einzelne Regel passt.** Zwei gleichzeitige
Fehlvorstellungen sind die aufwendigere Erklärung und kommen erst zum Zug, wenn die
einfache versagt.

**Diagnose und Vermutung stehen in getrennten Spalten.** Sonst wäre die Trefferquote
geschönt, denn Aufgaben einer Kategorie provozieren meist Fehler eben dieser Kategorie.

**beta steht auf 0 und wird nicht aus dem Basisgewicht abgeleitet.** In PFA ist beta die aus
Daten geschätzte Schwierigkeit (Pavlik, Cen und Koedinger 2009). Aus dem Basisgewicht
abgeleitet sähe es nach einer Kalibrierung aus, die nicht stattgefunden hat. Die
Protokollspalten `erfolgeVorher` und `fehlerVorher` sind so angelegt, dass sich die
Parameter nach der Studie aus den Logdaten schätzen lassen.

**Ein Tippfehler zählt nicht als Fehlversuch.** Eine Eingabe, die sich nicht als Java-Wert
lesen lässt, wird zurückgewiesen, ohne den Versuchszähler zu erhöhen oder ins Protokoll zu
gehen.

**Die Oberfläche ist serverseitig gerendert.** Der gesamte Zustand liegt ohnehin in Java.
Eine zweite Kopie im Browser könnte davon abweichen, und diese Abweichung stünde dann in
den Logdaten.

## Stand

Die Implementierung für FQ2 ist abgeschlossen. Für die Evaluationsstudie fehlen noch Vor-
und Nachtest sowie ein druckbares Gesprächsblatt für das Interview. Die Materialien,
also Einwilligung, zwei Fragebögen und Interviewleitfaden, liegen als LaTeX vor.

## Hinweis zu den Daten

`protokolle/` enthält die Logdaten der Teilnehmenden, `.env` die Zugangsdaten der
Datenbank. Beide stehen in der `.gitignore` und gehören nicht in das Repository.