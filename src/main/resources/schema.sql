-- Tabellen fuer die Studiendaten.
--
-- Spring Boot fuehrt diese Datei beim Start aus, wenn tutor.datenbank aktiv ist. Beide
-- Tabellen werden nur angelegt, wenn sie fehlen, ein Neustart loescht also nichts.
--
-- Zur Gestaltung: Es wird ausschliesslich angehaengt, nie geaendert und nie geloescht.
-- Das ist die wichtigste Eigenschaft fuer Studiendaten. Zwei Sitzungen, die versehentlich
-- unter demselben Pseudonym laufen, ueberschreiben sich dadurch nicht, sondern stehen als
-- zwei Sitzungen mit verschiedener sitzung_id nebeneinander und lassen sich hinterher
-- auseinanderhalten. Bei Dateien waere die zweite Sitzung ueber die erste geschrieben
-- worden, und das waere erst bei der Auswertung aufgefallen.
--
-- utf8mb4 deshalb, weil in den Ausdruecken Anfuehrungszeichen und Umlaute vorkommen und
-- das aeltere utf8 in MariaDB nicht den vollen Zeichenvorrat abdeckt.

CREATE TABLE IF NOT EXISTS protokoll (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    sitzung_id          CHAR(36)     NOT NULL,
    zeitpunkt           DATETIME(3)  NOT NULL,
    teilnehmer          VARCHAR(64)  NOT NULL,
    gruppe              VARCHAR(16)  NOT NULL,
    kategorie           VARCHAR(16)  NOT NULL,
    ausdruck            TEXT         NOT NULL,
    belegung            TEXT,
    referenz            VARCHAR(255),
    antwort             VARCHAR(255),
    versuch             INT          NOT NULL,
    korrekt             BOOLEAN      NOT NULL,
    diagnose            VARCHAR(64),
    vermutung           VARCHAR(64),
    erklaert            BOOLEAN      NOT NULL,
    konfidenz           VARCHAR(32)  NOT NULL,
    feedback_stufe      INT          NOT NULL,
    historie_kategorie  VARCHAR(16),
    erfolge_vorher      INT          NOT NULL,
    fehler_vorher       INT          NOT NULL,
    dauer_millis        BIGINT       NOT NULL,
    INDEX idx_protokoll_sitzung (sitzung_id),
    INDEX idx_protokoll_teilnehmer (teilnehmer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bewertung (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    sitzung_id          CHAR(36)     NOT NULL,
    zeitpunkt           DATETIME(3)  NOT NULL,
    teilnehmer          VARCHAR(64)  NOT NULL,
    gruppe              VARCHAR(16)  NOT NULL,
    aufgaben_nummer     INT          NOT NULL,
    kategorie           VARCHAR(16)  NOT NULL,
    ausdruck            TEXT         NOT NULL,
    versuche            INT          NOT NULL,
    korrekt             BOOLEAN      NOT NULL,
    feedback_stufe      INT          NOT NULL,
    diagnose            VARCHAR(64),
    erklaert            BOOLEAN      NOT NULL,
    bewertung           INT          NOT NULL,
    INDEX idx_bewertung_sitzung (sitzung_id),
    INDEX idx_bewertung_teilnehmer (teilnehmer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
