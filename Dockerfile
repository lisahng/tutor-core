# Abbild der Uebungsoberflaeche.
#
# Zwei Stufen: Die erste uebersetzt das Projekt, die zweite enthaelt nur noch die fertige
# JAR-Datei und eine Laufzeitumgebung. Das Abbild bleibt dadurch klein, und Maven samt
# heruntergeladenen Bibliotheken landet nicht auf dem Zielrechner.

FROM maven:3.9-eclipse-temurin-21 AS bau
WORKDIR /bau

# Erst nur die pom.xml kopieren und die Abhaengigkeiten laden. Diese Schicht wird
# zwischengespeichert, solange sich die pom.xml nicht aendert, und ein erneutes Bauen nach
# einer Codeaenderung dauert dadurch Sekunden statt Minuten.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Ohne -q, damit im Fehlerfall sichtbar ist, woran Maven scheitert. Mit -q bleibt eine
# fehlgeschlagene Uebersetzung stumm, und der Fehler faellt erst weiter unten auf, wo die
# JAR-Datei fehlt. Das fuehrt in die Irre.
RUN mvn -B clean package -DskipTests

# Auf einen festen Namen bringen. Sonst steht die Versionsnummer aus der pom.xml an zwei
# Stellen, und beim naechsten Versionssprung bricht der Bau an einer Stelle ab, die mit der
# Ursache nichts zu tun hat. Liegen wider Erwarten mehrere JAR-Dateien im Ordner, bricht cp
# mit einer klaren Meldung ab, statt stillschweigend die falsche zu nehmen.
RUN cp target/*.jar /bau/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

# Ohne diese Zeile laufen die Zeitstempel im Protokoll in UTC, und die Zuordnung zu den
# Terminen wird muehsam.
ENV TZ=Europe/Berlin

# Kein Betrieb als root.
RUN useradd --create-home --uid 10001 tutor
USER tutor

COPY --from=bau /bau/app.jar app.jar

# Hier landen die CSV-Dateien. Das Verzeichnis muss beim Start eingehaengt werden, sonst
# verschwinden sie mit dem Container.
VOLUME ["/app/protokolle"]
ENV TUTOR_PROTOKOLL_ORDNER=/app/protokolle

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]