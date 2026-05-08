package com.flowernailbar.repository;

import com.flowernailbar.model.AvailabilitySlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AvailabilitySlotRepository — raw JDBC access to availability_slots table.
 *
 * CONCURRENCY CONTROL:
 *   Uses optimistic locking via the `version` column.
 *   The UPDATE in markAsBooked checks both id AND version.
 *   If another transaction already booked the slot, version will have
 *   incremented and our UPDATE will affect 0 rows → we detect this and
 *   throw a ConcurrentBookingException, preventing double-booking.
 */
@Repository
public class AvailabilitySlotRepository {

    private static final Logger log = LoggerFactory.getLogger(AvailabilitySlotRepository.class);

    @Autowired
    private JdbcTemplate jdbc;

    private final RowMapper<AvailabilitySlot> slotRowMapper = (rs, rowNum) -> {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(rs.getLong("id"));
        slot.setSlotDate(rs.getString("slot_date"));
        slot.setSlotTime(rs.getString("slot_time"));
        slot.setLocation(rs.getString("location"));
        slot.setBooked(rs.getInt("is_booked") == 1);
        slot.setVersion(rs.getInt("version"));
        return slot;
    };

    /**
     * Find all available (not booked) slots for a given date and location.
     */
    public List<AvailabilitySlot> findAvailableByDateAndLocation(String date, String location) {
        return jdbc.query(
            "SELECT * FROM availability_slots WHERE slot_date = ? AND location = ? AND is_booked = 0 ORDER BY slot_time ASC",
            slotRowMapper, date, location
        );
    }

    /**
     * Find ALL slots for a date/location, marking a slot as booked only if
     * the specified technician already has a CONFIRMED appointment at that slot.
     * Other technicians' bookings do not affect availability here.
     */
    public List<AvailabilitySlot> findAllByDateAndLocationForTechnician(String date, String location, Long technicianId) {
        return jdbc.query("""
            SELECT s.id, s.slot_date, s.slot_time, s.location, s.version,
                   CASE WHEN a.id IS NOT NULL THEN 1 ELSE 0 END AS is_booked
            FROM availability_slots s
            LEFT JOIN appointments a
                ON a.slot_id = s.id
               AND a.technician_id = ?
               AND a.status = 'CONFIRMED'
            WHERE s.slot_date = ? AND s.location = ?
            ORDER BY s.slot_time ASC
        """, slotRowMapper, technicianId, date, location);
    }

    /**
     * Find ALL slots (booked + available) for a given date and location.
     * Used by the booking UI so customers can see which times are already taken.
     */
    public List<AvailabilitySlot> findAllByDateAndLocation(String date, String location) {
        return jdbc.query(
            "SELECT * FROM availability_slots WHERE slot_date = ? AND location = ? ORDER BY slot_time ASC",
            slotRowMapper, date, location
        );
    }

    /**
     * Find all distinct future dates (today onwards) for a location.
     */
    public List<String> findAvailableDatesByLocation(String location) {
        return jdbc.queryForList(
            "SELECT DISTINCT slot_date FROM availability_slots WHERE location = ? AND slot_date >= date('now','localtime') ORDER BY slot_date ASC",
            String.class, location
        );
    }

    /**
     * Find a slot by ID.
     */
    public Optional<AvailabilitySlot> findById(Long id) {
        List<AvailabilitySlot> results = jdbc.query(
            "SELECT * FROM availability_slots WHERE id = ?",
            slotRowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Optimistic locking update: marks a slot as booked ONLY if its current
     * version matches the expected version. This prevents double-booking.
     *
     * SQL logic:
     *   UPDATE ... WHERE id = ? AND version = ? AND is_booked = 0
     *
     * If rows affected == 0, another concurrent transaction already booked it.
     *
     * @return true if successfully marked as booked; false if concurrency conflict
     */
    public boolean markAsBooked(Long slotId, int expectedVersion) {
        int rowsAffected = jdbc.update(
            "UPDATE availability_slots SET is_booked = 1, version = version + 1 WHERE id = ? AND version = ? AND is_booked = 0",
            slotId, expectedVersion
        );
        if (rowsAffected == 0) {
            log.warn("[SlotRepo] Optimistic lock failed for slotId={} expectedVersion={} — slot may already be booked.", slotId, expectedVersion);
            return false;
        }
        log.info("[SlotRepo] Slot id={} successfully marked as booked.", slotId);
        return true;
    }

    /**
     * Release a slot back to available (used when appointment is cancelled).
     */
    public void markAsAvailable(Long slotId) {
        jdbc.update(
            "UPDATE availability_slots SET is_booked = 0, version = version + 1 WHERE id = ?",
            slotId
        );
        log.info("[SlotRepo] Slot id={} released back to available.", slotId);
    }

    /**
     * Get all slots (for admin/provider view).
     */
    public List<AvailabilitySlot> findAll() {
        return jdbc.query("SELECT * FROM availability_slots ORDER BY slot_date, slot_time", slotRowMapper);
    }
}
