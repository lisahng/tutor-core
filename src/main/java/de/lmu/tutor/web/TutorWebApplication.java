package de.lmu.tutor.web;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import de.lmu.tutor.buglib.BugLibrary;

/**
 * Startpunkt der Weboberflaeche.
 *
 * <p>Die Oberflaeche ist eine Schale um die bestehende Kernlogik. Sie bringt keine eigene
 * Fachlogik mit, sondern nimmt Eingaben entgegen und zeigt an, was {@link Uebungsablauf}
 * und {@link de.lmu.tutor.session.Uebungssitzung} liefern. Entsprechend laesst sich alles
 * Inhaltliche weiterhin ohne Webserver testen.</p>
 *
 * <p>Starten mit:
 * <pre>  mvn spring-boot:run  </pre>
 * Danach ist die Oberflaeche unter <a href="http://localhost:8080">http://localhost:8080</a>
 * erreichbar.</p>
 *
 * <p><b>Zur Bug Library als Bean.</b> Sie wird einmal beim Start geladen und von allen
 * Browsersitzungen geteilt. Das ist unbedenklich, weil sie nach dem Laden nur gelesen wird.
 * Jede Browsersitzung bekommt dagegen ihr eigenes {@link de.lmu.tutor.student.Studentenmodell},
 * denn der Kenntnisstand gehoert zur einzelnen Person.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(Sitzungseinstellungen.class)
public class TutorWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorWebApplication.class, args);
    }

    /** Die Fehlvorstellungen aus {@code bug-library.json}, einmal geladen. */
    @Bean
    public BugLibrary bugLibrary() {
        return BugLibrary.loadDefault();
    }

    /** Die Uhr fuer Restzeit und Zeitstempel, im Test ersetzbar. */
    @Bean
    public Clock uhr() {
        return Clock.systemDefaultZone();
    }
}
