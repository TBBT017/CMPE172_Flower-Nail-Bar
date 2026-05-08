package com.flowernailbar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;

/**
 * DatabaseInitializer — runs on startup to create tables (if not exists)
 * and seed the availability_slots table with sample data.
 *
 * Uses raw JDBC via JdbcTemplate. No ORM/Hibernate involved.
 */
@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        log.info("[DB INIT] Starting database schema initialization...");
        createTables();
        runMigrations();
        seedData();
        log.info("[DB INIT] Database ready.");
    }

    /**
     * Lightweight migrations for schema changes that need to apply to
     * already-existing databases (CREATE TABLE IF NOT EXISTS is a no-op
     * on existing tables, so column additions need this).
     */
    private void runMigrations() {
        addColumnIfMissing("users", "password_hash", "TEXT");
        addColumnIfMissing("users", "role", "TEXT NOT NULL DEFAULT 'USER'");
        addColumnIfMissing("appointments", "technician_id", "INTEGER");
    }

    private void addColumnIfMissing(String table, String column, String typeDecl) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + typeDecl);
            log.info("[DB INIT] Migration: added column {}.{} ({})", table, column, typeDecl);
        } catch (Exception e) {
            // SQLite throws when the column already exists — that's fine, swallow it.
            log.debug("[DB INIT] Column {}.{} already present (skip): {}", table, column, e.getMessage());
        }
    }

    private void createTables() {
        // USERS table
        // password_hash is nullable so legacy guest-booking rows (created before
        // the auth feature) don't break. role is 'USER' or 'ADMIN'.
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name     TEXT    NOT NULL,
                email         TEXT    NOT NULL UNIQUE,
                phone         TEXT    NOT NULL,
                password_hash TEXT,
                role          TEXT    NOT NULL DEFAULT 'USER',
                created_at    TEXT    DEFAULT (datetime('now'))
            )
        """);

        // SERVICES table
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS services (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                name         TEXT    NOT NULL,
                duration_min INTEGER NOT NULL,
                price        REAL    NOT NULL,
                location     TEXT    NOT NULL
            )
        """);

        // AVAILABILITY_SLOTS table
        // version column for optimistic locking (concurrency control)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS availability_slots (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                slot_date   TEXT    NOT NULL,
                slot_time   TEXT    NOT NULL,
                location    TEXT    NOT NULL,
                is_booked   INTEGER NOT NULL DEFAULT 0,
                version     INTEGER NOT NULL DEFAULT 0
            )
        """);

        // TECHNICIANS table
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS technicians (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                name      TEXT    NOT NULL,
                specialty TEXT    NOT NULL,
                location  TEXT    NOT NULL
            )
        """);

        // APPOINTMENTS table
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS appointments (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id        INTEGER NOT NULL,
                service_id     INTEGER NOT NULL,
                slot_id        INTEGER NOT NULL,
                technician_id  INTEGER,
                status         TEXT    NOT NULL DEFAULT 'CONFIRMED',
                booked_at      TEXT    DEFAULT (datetime('now')),
                FOREIGN KEY (user_id)       REFERENCES users(id),
                FOREIGN KEY (service_id)    REFERENCES services(id),
                FOREIGN KEY (slot_id)       REFERENCES availability_slots(id),
                FOREIGN KEY (technician_id) REFERENCES technicians(id)
            )
        """);

        log.info("[DB INIT] Tables created (if not existed).");
    }

    private void seedData() {
        // Seed the built-in admin account if not present.
        // Login: tran1 / pass1234 (the "email" column holds the literal username).
        Integer adminCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "tran1");
        if (adminCount != null && adminCount == 0) {
            String hash = new BCryptPasswordEncoder().encode("pass1234");
            jdbc.update(
                "INSERT INTO users (full_name, email, phone, password_hash, role) VALUES (?, ?, ?, ?, 'ADMIN')",
                "Tran (Admin)", "tran1", "000-000-0000", hash
            );
            log.info("[DB INIT] Seeded ADMIN user: login='tran1' password='pass1234'");
        } else {
            // Make sure the existing tran1 row really is ADMIN (in case it was
            // created earlier with the default 'USER' role).
            jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ? AND role <> 'ADMIN'", "tran1");
        }

        // Seed technicians only if empty
        Integer techCount = jdbc.queryForObject("SELECT COUNT(*) FROM technicians", Integer.class);
        if (techCount != null && techCount == 0) {
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Mary', 'Nail Art & Design', 'San Jose')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Gaby', 'GEL Specialist', 'San Jose')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Kevin', 'Pedicure Expert', 'San Jose')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Sophia', 'Classic Nails', 'San Jose')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Rose', 'Nail Art & Design', 'Sunnyvale')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('May', 'GEL Specialist', 'Sunnyvale')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('July', 'Pedicure Expert', 'Sunnyvale')");
            jdbc.execute("INSERT INTO technicians (name, specialty, location) VALUES ('Ruby', 'Classic Nails', 'Sunnyvale')");
            log.info("[DB INIT] Technicians seeded.");
        }

        // Seed services only if empty
        Integer serviceCount = jdbc.queryForObject("SELECT COUNT(*) FROM services", Integer.class);
        if (serviceCount != null && serviceCount == 0) {
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Classic Manicure', 45, 55.0, 'San Jose')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('GEL Manicure', 55, 65.0, 'San Jose')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Spa Pedicure', 65, 55.0, 'San Jose')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Manicure + Pedicure', 120, 105.0, 'San Jose')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Classic Manicure', 45, 55.0, 'Sunnyvale')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('GEL Manicure', 55, 65.0, 'Sunnyvale')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Spa Pedicure', 65, 55.0, 'Sunnyvale')");
            jdbc.execute("INSERT INTO services (name, duration_min, price, location) VALUES ('Manicure + Pedicure', 120, 105.0, 'Sunnyvale')");
            log.info("[DB INIT] Services seeded.");
        }

        // Ensure slots exist for the next 14 days from today.
        // Runs on every startup so the calendar never goes stale.
        String[] locations = {"San Jose", "Sunnyvale"};
        String[] times = {
            "09:00 AM", "09:30 AM", "10:00 AM",
            "10:30 AM", "11:00 AM", "11:30 AM",
            "12:00 PM", "12:30 PM", "01:00 PM",
            "01:30 PM", "02:00 PM", "02:30 PM"
        };
        LocalDate today = LocalDate.now();
        int newSlots = 0;
        for (int i = 0; i < 14; i++) {
            String date = today.plusDays(i).toString();
            for (String loc : locations) {
                Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM availability_slots WHERE slot_date = ? AND location = ?",
                    Integer.class, date, loc
                );
                if (count == null || count == 0) {
                    for (String time : times) {
                        jdbc.update(
                            "INSERT INTO availability_slots (slot_date, slot_time, location, is_booked, version) VALUES (?, ?, ?, 0, 0)",
                            date, time, loc
                        );
                    }
                    newSlots += times.length;
                }
            }
        }
        if (newSlots > 0) {
            log.info("[DB INIT] Added {} new availability slots for the next 14 days.", newSlots);
        }
    }
}
