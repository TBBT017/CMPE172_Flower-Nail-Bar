package com.flowernailbar.repository;

import com.flowernailbar.model.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * AppointmentRepository — raw JDBC access to the appointments table.
 * JOIN queries pull in user, service, and slot data for full appointment views.
 */
@Repository
public class AppointmentRepository {

    private static final Logger log = LoggerFactory.getLogger(AppointmentRepository.class);

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Full row mapper with JOINed user, service, and slot fields.
     */
    private final RowMapper<Appointment> fullAppointmentRowMapper = (rs, rowNum) -> {
        Appointment a = new Appointment();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        a.setServiceId(rs.getLong("service_id"));
        a.setSlotId(rs.getLong("slot_id"));
        a.setStatus(rs.getString("status"));
        a.setBookedAt(rs.getString("booked_at"));
        // Joined fields
        a.setUserFullName(rs.getString("full_name"));
        a.setUserEmail(rs.getString("email"));
        a.setUserPhone(rs.getString("phone"));
        a.setServiceName(rs.getString("service_name"));
        a.setServiceDurationMin(rs.getInt("duration_min"));
        a.setServicePrice(rs.getDouble("price"));
        a.setSlotDate(rs.getString("slot_date"));
        a.setSlotTime(rs.getString("slot_time"));
        a.setLocation(rs.getString("location"));
        long techId = rs.getLong("technician_id");
        if (!rs.wasNull()) {
            a.setTechnicianId(techId);
            a.setTechnicianName(rs.getString("technician_name"));
        }
        return a;
    };

    private static final String FULL_SELECT = """
        SELECT a.id, a.user_id, a.service_id, a.slot_id, a.technician_id, a.status, a.booked_at,
               u.full_name, u.email, u.phone,
               s.name AS service_name, s.duration_min, s.price,
               sl.slot_date, sl.slot_time, sl.location,
               t.name AS technician_name
        FROM appointments a
        JOIN users u  ON a.user_id    = u.id
        JOIN services s ON a.service_id = s.id
        JOIN availability_slots sl ON a.slot_id = sl.id
        LEFT JOIN technicians t ON a.technician_id = t.id
    """;

    /**
     * Save a new appointment and return the generated ID.
     */
    public Long save(Long userId, Long serviceId, Long slotId, Long technicianId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO appointments (user_id, service_id, slot_id, technician_id, status) VALUES (?, ?, ?, ?, 'CONFIRMED')",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, serviceId);
            ps.setLong(3, slotId);
            if (technicianId != null) {
                ps.setLong(4, technicianId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            return ps;
        }, keyHolder);
        Long newId = keyHolder.getKey().longValue();
        log.info("[AppointmentRepo] Saved appointment id={} userId={} slotId={} technicianId={}", newId, userId, slotId, technicianId);
        return newId;
    }

    /**
     * Find appointment by ID with all joined details.
     */
    public Optional<Appointment> findById(Long id) {
        List<Appointment> results = jdbc.query(
            FULL_SELECT + " WHERE a.id = ?",
            fullAppointmentRowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find all appointments for a specific user (by email).
     */
    public List<Appointment> findByUserEmail(String email) {
        return jdbc.query(
            FULL_SELECT + " WHERE u.email = ? ORDER BY sl.slot_date DESC, sl.slot_time DESC",
            fullAppointmentRowMapper, email
        );
    }

    /**
     * Get all appointments (admin/provider view), newest first.
     */
    public List<Appointment> findAll() {
        return jdbc.query(
            FULL_SELECT + " ORDER BY sl.slot_date DESC, sl.slot_time DESC",
            fullAppointmentRowMapper
        );
    }

    /**
     * Check whether a technician already has a confirmed booking for a given slot.
     * Used before inserting a new appointment to prevent double-booking per technician.
     */
    public boolean existsByTechnicianAndSlot(Long technicianId, Long slotId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM appointments WHERE technician_id = ? AND slot_id = ? AND status = 'CONFIRMED'",
            Integer.class, technicianId, slotId
        );
        return count != null && count > 0;
    }

    /**
     * Cancel an appointment by setting status to CANCELLED.
     */
    public void cancel(Long appointmentId) {
        jdbc.update("UPDATE appointments SET status = 'CANCELLED' WHERE id = ?", appointmentId);
        log.info("[AppointmentRepo] Appointment id={} cancelled.", appointmentId);
    }

    /**
     * Count bookings per hour (metric for M6 system management).
     */
    public int countBookingsInLastHour() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM appointments WHERE booked_at >= datetime('now', '-1 hour') AND status = 'CONFIRMED'",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    /**
     * Count cancelled/failed bookings (metric for M6).
     */
    public int countCancelledBookings() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM appointments WHERE status = 'CANCELLED'",
            Integer.class
        );
        return count == null ? 0 : count;
    }
}
