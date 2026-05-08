package com.flowernailbar.controller;

import com.flowernailbar.model.Appointment;
import com.flowernailbar.model.BookingRequest;
import com.flowernailbar.service.AppointmentService;
import com.flowernailbar.service.ConcurrentBookingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AppointmentController — REST API for appointment management.
 *
 * M5 Distribution Boundary:
 *   POST /appointments/bookAppointment calls the NotificationController
 *   (mock external service) after booking. Single coarse-grained call.
 *
 * Endpoints:
 *   POST /appointments/bookAppointment  — book + notify
 *   GET  /appointments/list             — list all appointments
 *   GET  /appointments/{id}             — get single appointment
 *   POST /appointments/{id}/cancel      — cancel appointment
 */
@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    @Autowired
    private AppointmentService appointmentService;

    /**
     * M5 Main endpoint: books an appointment AND calls the mock notification service.
     * Coarse-grained: one HTTP call does the full operation.
     */
    @PostMapping("/bookAppointment")
    public ResponseEntity<?> bookAppointment(@RequestBody BookingRequest request) {
        log.info("[AppointmentController] POST /appointments/bookAppointment serviceId={} slotId={}",
            request.getServiceId(), request.getSlotId());

        try {
            Appointment appointment = appointmentService.bookAppointment(request);
            return ResponseEntity.ok(Map.of(
                "message", "Appointment booked successfully. Confirmation sent!",
                "appointmentId", appointment.getId(),
                "service", appointment.getServiceName(),
                "date", appointment.getSlotDate(),
                "time", appointment.getSlotTime(),
                "status", appointment.getStatus()
            ));
        } catch (ConcurrentBookingException e) {
            log.warn("[AppointmentController] Concurrent conflict: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[AppointmentController] Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Booking failed. Please try again."));
        }
    }

    /**
     * List all appointments (REST endpoint for API clients).
     */
    @GetMapping("/list")
    public ResponseEntity<List<Appointment>> listAppointments(
        @RequestParam(required = false) String email
    ) {
        log.info("[AppointmentController] GET /appointments/list email={}", email);
        List<Appointment> appointments = (email != null && !email.isBlank())
            ? appointmentService.getAppointmentsByEmail(email)
            : appointmentService.getAllAppointments();
        return ResponseEntity.ok(appointments);
    }

    /**
     * Get a single appointment by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAppointment(@PathVariable Long id) {
        return appointmentService.getAppointmentById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
