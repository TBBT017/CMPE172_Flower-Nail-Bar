package com.flowernailbar.controller;

import com.flowernailbar.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HealthController — System management and monitoring (M6).
 *
 * GET /api/health → returns system health status
 * GET /api/metrics → returns booking metrics
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppointmentService appointmentService;

    /**
     * Health check endpoint.
     * Returns: status, timestamp, DB connectivity.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("[HealthController] GET /api/health");

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("application", "Flower Nail Bar Appointment System");
        health.put("timestamp", LocalDateTime.now().toString());

        // Check DB connectivity
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            health.put("database", "UP");
            health.put("databaseType", "SQLite");
        } catch (Exception e) {
            log.error("[HealthController] DB check failed: {}", e.getMessage());
            health.put("database", "DOWN");
            health.put("databaseError", e.getMessage());
            health.put("status", "DEGRADED");
        }

        // Check notification service
        health.put("notificationService", "UP (mock)");

        log.info("[HealthController] Health: {}", health.get("status"));
        return ResponseEntity.ok(health);
    }

    /**
     * Metrics endpoint — booking statistics for monitoring (M6).
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        log.info("[HealthController] GET /api/metrics");

        Map<String, Object> metrics = new LinkedHashMap<>();

        try {
            Integer totalAppointments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE status = 'CONFIRMED'", Integer.class);
            Integer cancelledAppointments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE status = 'CANCELLED'", Integer.class);
            Integer availableSlots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM availability_slots WHERE is_booked = 0", Integer.class);
            Integer totalSlots = jdbc.queryForObject(
                "SELECT COUNT(*) FROM availability_slots", Integer.class);

            metrics.put("confirmedAppointments", totalAppointments);
            metrics.put("cancelledAppointments", cancelledAppointments);
            metrics.put("bookingsLastHour", appointmentService.getBookingsLastHour());
            metrics.put("availableSlots", availableSlots);
            metrics.put("totalSlots", totalSlots);
            metrics.put("slotUtilizationPct",
                totalSlots != null && totalSlots > 0
                    ? String.format("%.1f%%", ((totalSlots - availableSlots) * 100.0 / totalSlots))
                    : "0%");
            metrics.put("timestamp", LocalDateTime.now().toString());

        } catch (Exception e) {
            log.error("[HealthController] Metrics collection failed: {}", e.getMessage());
            metrics.put("error", "Could not collect metrics: " + e.getMessage());
        }

        return ResponseEntity.ok(metrics);
    }
}
