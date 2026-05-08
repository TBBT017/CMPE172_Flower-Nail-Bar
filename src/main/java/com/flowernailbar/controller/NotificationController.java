package com.flowernailbar.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * NotificationController — Mock External Notification Service (M5).
 *
 * This controller simulates a remote notification/email service.
 * In production, this would be a separate deployed microservice.
 * For this project, it runs in the same Spring Boot app to demonstrate
 * the distribution boundary concept.
 *
 * The API is coarse-grained: a single POST /notification/send call
 * handles the full notification, encapsulating all details server-side.
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    /**
     * Simulates sending a booking confirmation notification.
     * In production: would send an email or SMS.
     *
     * @param appointmentId the appointment ID
     * @param email         recipient email
     * @param service       service name
     * @param date          appointment date
     * @param time          appointment time
     */
    @PostMapping("/send")
    public String sendNotification(
        @RequestParam(required = false) Long appointmentId,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String service,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String time
    ) {
        log.info("[NotificationService] Sending confirmation — appointmentId={} to={} service={} on {} at {}",
            appointmentId, email, service, date, time);

        // Simulate notification work
        String confirmationMessage = String.format(
            "Confirmation sent to %s for %s on %s at %s (Appointment #%d)",
            email != null ? email : "customer",
            service != null ? service : "service",
            date != null ? date : "date",
            time != null ? time : "time",
            appointmentId != null ? appointmentId : 0
        );

        log.info("[NotificationService] {}", confirmationMessage);
        return "Confirmation sent!";
    }

    /**
     * Health check for the notification service endpoint.
     */
    @GetMapping("/status")
    public String status() {
        return "Notification service is running.";
    }
}
