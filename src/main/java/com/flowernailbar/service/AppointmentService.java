package com.flowernailbar.service;

import com.flowernailbar.model.Appointment;
import com.flowernailbar.model.AvailabilitySlot;
import com.flowernailbar.model.BookingRequest;
import com.flowernailbar.repository.AppointmentRepository;
import com.flowernailbar.repository.AvailabilitySlotRepository;
import com.flowernailbar.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * AppointmentService — core business logic layer.
 *
 * CONCURRENCY CONTROL (M4):
 *   bookAppointment() is annotated with @Transactional(isolation = SERIALIZABLE).
 *   Inside, we use optimistic locking by reading the slot's current version,
 *   then calling markAsBooked(id, version) which does:
 *     UPDATE ... WHERE id = ? AND version = ? AND is_booked = 0
 *   If 0 rows are affected, a concurrent transaction already booked it,
 *   and we throw ConcurrentBookingException.
 *
 * DISTRIBUTION BOUNDARY (M5):
 *   After a successful booking, we call the mock NotificationController
 *   via RestTemplate (HTTP POST to /notification/send). This simulates
 *   a coarse-grained remote service call.
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    @Autowired
    private AppointmentRepository appointmentRepo;

    @Autowired
    private AvailabilitySlotRepository slotRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    /**
     * Book an appointment.
     *
     * ACID reasoning:
     *  - Atomicity:   If any step fails (slot taken, DB error), the whole transaction rolls back.
     *  - Consistency: Slot remains consistent — it can't be booked=1 without a matching appointment row.
     *  - Isolation:   SERIALIZABLE prevents two concurrent transactions from both reading is_booked=0.
     *  - Durability:  Once committed, the booking survives restarts (SQLite WAL mode).
     *
     * @throws ConcurrentBookingException if slot was taken by another concurrent request.
     * @throws IllegalArgumentException   if slot or service not found.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public Appointment bookAppointment(BookingRequest request) {
        log.info("[AppointmentService] Booking request: slotId={} serviceId={} email={}",
            request.getSlotId(), request.getServiceId(), request.getEmail());

        // Step 1: Load the slot and verify it exists and is available
        Optional<AvailabilitySlot> slotOpt = slotRepo.findById(request.getSlotId());
        if (slotOpt.isEmpty()) {
            log.error("[AppointmentService] Slot not found: id={}", request.getSlotId());
            throw new IllegalArgumentException("Time slot not found.");
        }

        AvailabilitySlot slot = slotOpt.get();

        // Step 2: Check if this technician is already booked for this slot.
        // The SERIALIZABLE transaction ensures this check + insert are atomic,
        // so two concurrent requests for the same technician+slot cannot both succeed.
        if (request.getTechnicianId() != null) {
            boolean alreadyBooked = appointmentRepo.existsByTechnicianAndSlot(
                request.getTechnicianId(), slot.getId()
            );
            if (alreadyBooked) {
                log.warn("[AppointmentService] Technician id={} already booked for slotId={}",
                    request.getTechnicianId(), slot.getId());
                throw new ConcurrentBookingException(
                    "This technician is no longer available at that time. Please choose a different technician or time."
                );
            }
        }

        // Step 3: Find or create the user (upsert by email)
        Long userId = userRepo.findOrCreate(request.getFullName(), request.getEmail(), request.getPhone());

        // Step 4: Save the appointment record
        Long appointmentId = appointmentRepo.save(userId, request.getServiceId(), request.getSlotId(), request.getTechnicianId());

        // Step 5: Fetch the full appointment details to return
        Appointment appointment = appointmentRepo.findById(appointmentId)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved appointment."));

        log.info("[AppointmentService] Appointment confirmed: id={} user={} slot={} {}",
            appointmentId, request.getEmail(), slot.getSlotDate(), slot.getSlotTime());

        // Step 6: Call mock notification service (distribution boundary — M5)
        // This is outside the @Transactional scope intentionally:
        // notification failure should NOT roll back a successful booking.
        sendNotification(appointment);

        return appointment;
    }

    /**
     * Cancel an appointment and release the slot back to available.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void cancelAppointment(Long appointmentId) {
        log.info("[AppointmentService] Cancelling appointment id={}", appointmentId);

        Optional<Appointment> apptOpt = appointmentRepo.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            throw new IllegalArgumentException("Appointment not found.");
        }

        Appointment appt = apptOpt.get();
        if ("CANCELLED".equals(appt.getStatus())) {
            throw new IllegalStateException("Appointment is already cancelled.");
        }

        appointmentRepo.cancel(appointmentId);

        log.info("[AppointmentService] Appointment id={} cancelled, technician slot released.",
            appointmentId);
    }

    /**
     * Get all appointments (provider/admin view).
     */
    public List<Appointment> getAllAppointments() {
        return appointmentRepo.findAll();
    }

    /**
     * Get appointments for a specific user by email.
     */
    public List<Appointment> getAppointmentsByEmail(String email) {
        return appointmentRepo.findByUserEmail(email);
    }

    /**
     * Get appointment by ID.
     */
    public Optional<Appointment> getAppointmentById(Long id) {
        return appointmentRepo.findById(id);
    }

    /**
     * Check if a technician is already confirmed for a given slot.
     * Used by the confirm page to catch races before form submission.
     */
    public boolean isTechnicianBooked(Long technicianId, Long slotId) {
        return appointmentRepo.existsByTechnicianAndSlot(technicianId, slotId);
    }

    /**
     * Get available slots for a given date and location.
     */
    public List<AvailabilitySlot> getAvailableSlots(String date, String location) {
        return slotRepo.findAvailableByDateAndLocation(date, location);
    }

    /**
     * Get available dates for a location.
     */
    public List<String> getAvailableDates(String location) {
        return slotRepo.findAvailableDatesByLocation(location);
    }

    /**
     * Call mock external notification service (M5 distribution boundary).
     * Coarse-grained: one call handles the entire notification.
     * Failure is logged but does NOT affect the booking transaction.
     */
    private void sendNotification(Appointment appointment) {
        try {
            String url = notificationServiceUrl
                + "?appointmentId=" + appointment.getId()
                + "&email=" + appointment.getUserEmail()
                + "&service=" + appointment.getServiceName()
                + "&date=" + appointment.getSlotDate()
                + "&time=" + appointment.getSlotTime();

            String response = restTemplate.postForObject(url, null, String.class);
            log.info("[AppointmentService] Notification sent for appointment id={}: {}", appointment.getId(), response);
        } catch (RestClientException e) {
            log.warn("[AppointmentService] Notification service call failed for appointment id={}: {}",
                appointment.getId(), e.getMessage());
            // Do NOT rethrow — notification failure doesn't undo a successful booking
        }
    }

    // Metrics for health/monitoring (M6)
    public int getBookingsLastHour() {
        return appointmentRepo.countBookingsInLastHour();
    }

    public int getCancelledBookings() {
        return appointmentRepo.countCancelledBookings();
    }
}
