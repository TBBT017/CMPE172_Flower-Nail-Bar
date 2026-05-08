package com.flowernailbar.controller;

import com.flowernailbar.model.Appointment;
import com.flowernailbar.model.AvailabilitySlot;
import com.flowernailbar.model.BookingRequest;
import com.flowernailbar.model.Service;
import com.flowernailbar.model.Technician;
import com.flowernailbar.model.User;
import com.flowernailbar.service.AppointmentService;
import com.flowernailbar.service.ConcurrentBookingException;
import com.flowernailbar.service.SlotService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * BookingController — handles the multi-step booking flow.
 *
 * Flow:
 *   GET /book?location=...                       → service selection page
 *   GET /book/technician?serviceId=X&location=Y  → technician selection page
 *   GET /book/slots?serviceId=X&technicianId=Z   → date+time slot picker
 *   GET /book/confirm?...                        → REQUIRES LOGIN. customer info form + summary
 *   POST /book/confirm                           → submit booking → redirect to confirmation
 *   GET /book/confirmation/{id}                  → confirmation page
 *   GET /appointments                            → list all appointments (admin view)
 *   POST /appointments/{id}/cancel               → cancel an appointment
 */
@Controller
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private SlotService slotService;

    // ─────────────────────────────────────────────
    // Step 1: Select Service
    // ─────────────────────────────────────────────

    @GetMapping("/book")
    public String selectService(
        @RequestParam(defaultValue = "San Jose") String location,
        Model model
    ) {
        log.info("[BookingController] GET /book location={}", location);
        List<Service> services = slotService.getServicesByLocation(location);
        model.addAttribute("services", services);
        model.addAttribute("location", location);
        return "select-service";
    }

    // ─────────────────────────────────────────────
    // Step 2: Select Technician
    // ─────────────────────────────────────────────

    @GetMapping("/book/technician")
    public String selectTechnician(
        @RequestParam Long serviceId,
        @RequestParam(defaultValue = "San Jose") String location,
        @RequestParam(required = false) String error,
        Model model
    ) {
        log.info("[BookingController] GET /book/technician serviceId={} location={}", serviceId, location);

        Optional<Service> serviceOpt = slotService.getServiceById(serviceId);
        if (serviceOpt.isEmpty()) {
            return "redirect:/book?location=" + location;
        }

        if ("technician_taken".equals(error)) {
            model.addAttribute("error", "That technician was just booked for your chosen time. Please select a different technician.");
        }

        List<Technician> technicians = slotService.getTechniciansByLocation(location);
        model.addAttribute("service", serviceOpt.get());
        model.addAttribute("location", location);
        model.addAttribute("technicians", technicians);
        return "select-technician";
    }

    // ─────────────────────────────────────────────
    // Step 3: Select Date + Time Slot
    // ─────────────────────────────────────────────

    @GetMapping("/book/slots")
    public String selectSlot(
        @RequestParam Long serviceId,
        @RequestParam Long technicianId,
        @RequestParam(defaultValue = "San Jose") String location,
        @RequestParam(required = false) String date,
        Model model
    ) {
        log.info("[BookingController] GET /book/slots serviceId={} technicianId={} location={} date={}", serviceId, technicianId, location, date);

        Optional<Service> serviceOpt = slotService.getServiceById(serviceId);
        if (serviceOpt.isEmpty()) {
            log.warn("[BookingController] Service not found: {}", serviceId);
            return "redirect:/book?location=" + location;
        }

        Optional<Technician> technicianOpt = slotService.getTechnicianById(technicianId);
        if (technicianOpt.isEmpty()) {
            log.warn("[BookingController] Technician not found: {}", technicianId);
            return "redirect:/book/technician?serviceId=" + serviceId + "&location=" + location;
        }

        List<String> availableDates = slotService.getAvailableDates(location);

        // Default to first available date if none selected
        if (date == null && !availableDates.isEmpty()) {
            date = availableDates.get(0);
        }

        // Show all slots for the day, marking blocked only if THIS technician is booked.
        List<AvailabilitySlot> slots = date != null
            ? slotService.getAllSlotsForDateAndTechnician(date, location, technicianId)
            : List.of();

        model.addAttribute("service", serviceOpt.get());
        model.addAttribute("technician", technicianOpt.get());
        model.addAttribute("technicianId", technicianId);
        model.addAttribute("location", location);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("selectedDate", date);
        model.addAttribute("slots", slots);
        return "select-slot";
    }

    // ─────────────────────────────────────────────
    // Step 3: Confirm Booking (info form) — REQUIRES LOGIN
    // ─────────────────────────────────────────────

    @GetMapping("/book/confirm")
    public String confirmForm(
        @RequestParam Long serviceId,
        @RequestParam Long slotId,
        @RequestParam(required = false) Long technicianId,
        HttpSession session,
        Model model
    ) {
        log.info("[BookingController] GET /book/confirm serviceId={} slotId={} technicianId={}", serviceId, slotId, technicianId);

        // Gate: user must be logged in to confirm.
        User currentUser = (User) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (currentUser == null) {
            String next = "/book/confirm?serviceId=" + serviceId + "&slotId=" + slotId
                + (technicianId != null ? "&technicianId=" + technicianId : "");
            log.info("[BookingController] Anonymous user hit /book/confirm — redirecting to /login");
            return "redirect:/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
        }

        Optional<Service> serviceOpt = slotService.getServiceById(serviceId);
        Optional<AvailabilitySlot> slotOpt = slotService.getSlotById(slotId);

        if (serviceOpt.isEmpty() || slotOpt.isEmpty()) {
            log.warn("[BookingController] Invalid confirm request — service or slot not found.");
            return "redirect:/book";
        }

        if (technicianId != null && appointmentService.isTechnicianBooked(technicianId, slotId)) {
            log.warn("[BookingController] Technician {} already booked for slot {}, redirecting.", technicianId, slotId);
            return "redirect:/book/technician?serviceId=" + serviceId
                + "&location=" + slotOpt.get().getLocation()
                + "&error=technician_taken";
        }

        // Pre-fill the booking request from the logged-in user.
        BookingRequest prefill = new BookingRequest();
        prefill.setFullName(currentUser.getFullName());
        prefill.setEmail(currentUser.getEmail());
        prefill.setPhone(currentUser.getPhone());
        prefill.setTechnicianId(technicianId);

        Optional<Technician> technicianOpt = technicianId != null
            ? slotService.getTechnicianById(technicianId)
            : Optional.empty();

        model.addAttribute("service", serviceOpt.get());
        model.addAttribute("slot", slotOpt.get());
        model.addAttribute("bookingRequest", prefill);
        technicianOpt.ifPresent(t -> model.addAttribute("technician", t));
        return "confirm-booking";
    }

    // ─────────────────────────────────────────────
    // Step 4: Submit Booking (POST) — REQUIRES LOGIN
    // ─────────────────────────────────────────────

    @PostMapping("/book/confirm")
    public String submitBooking(
        @ModelAttribute BookingRequest bookingRequest,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        // Gate: must be logged in.
        User currentUser = (User) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (currentUser == null) {
            String next = "/book/confirm?serviceId=" + bookingRequest.getServiceId()
                        + "&slotId=" + bookingRequest.getSlotId();
            return "redirect:/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
        }

        // Trust the session, not the form, for identity. (Form name/phone may
        // have been edited by the user — that's fine, we keep their original
        // session email so the appointment is linked to their account.)
        bookingRequest.setEmail(currentUser.getEmail());
        if (bookingRequest.getFullName() == null || bookingRequest.getFullName().isBlank()) {
            bookingRequest.setFullName(currentUser.getFullName());
        }
        if (bookingRequest.getPhone() == null || bookingRequest.getPhone().isBlank()) {
            bookingRequest.setPhone(currentUser.getPhone());
        }

        log.info("[BookingController] POST /book/confirm email={} slotId={} serviceId={}",
            bookingRequest.getEmail(), bookingRequest.getSlotId(), bookingRequest.getServiceId());

        try {
            Appointment appointment = appointmentService.bookAppointment(bookingRequest);
            log.info("[BookingController] Booking successful, appointment id={}", appointment.getId());
            return "redirect:/book/confirmation/" + appointment.getId();

        } catch (ConcurrentBookingException e) {
            log.warn("[BookingController] Concurrent booking conflict: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            String redirect = "redirect:/book/slots?serviceId=" + bookingRequest.getServiceId()
                + "&location=San Jose";
            if (bookingRequest.getTechnicianId() != null) {
                redirect += "&technicianId=" + bookingRequest.getTechnicianId();
            }
            return redirect;

        } catch (Exception e) {
            log.error("[BookingController] Booking failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Booking failed. Please try again.");
            return "redirect:/book";
        }
    }

    // ─────────────────────────────────────────────
    // Step 5: Confirmation Page
    // ─────────────────────────────────────────────

    @GetMapping("/book/confirmation/{id}")
    public String confirmationPage(@PathVariable Long id, Model model) {
        log.info("[BookingController] GET /book/confirmation/{}", id);

        Optional<Appointment> apptOpt = appointmentService.getAppointmentById(id);
        if (apptOpt.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("appointment", apptOpt.get());
        return "confirmation";
    }

    // ─────────────────────────────────────────────
    // Appointments List — ADMIN ONLY (sees every customer)
    // Regular users get bounced to /my-appointments.
    // ─────────────────────────────────────────────

    @GetMapping("/appointments")
    public String listAppointments(
        @RequestParam(required = false) String email,
        HttpSession session,
        Model model
    ) {
        User currentUser = (User) session.getAttribute(AuthController.SESSION_USER_KEY);

        // Anonymous → login first, with next= back here
        if (currentUser == null) {
            return "redirect:/login?next=" + URLEncoder.encode("/appointments", StandardCharsets.UTF_8);
        }
        // Logged in but not admin → forward to their own page (no peeking at others)
        if (!currentUser.isAdmin()) {
            log.info("[BookingController] Non-admin {} hit /appointments — redirecting to /my-appointments",
                currentUser.getEmail());
            return "redirect:/my-appointments";
        }

        log.info("[BookingController] GET /appointments (admin={}) filterEmail={}",
            currentUser.getEmail(), email);

        List<Appointment> appointments = (email != null && !email.isBlank())
            ? appointmentService.getAppointmentsByEmail(email)
            : appointmentService.getAllAppointments();

        model.addAttribute("appointments", appointments);
        model.addAttribute("searchEmail", email);
        return "appointments";
    }

    // ─────────────────────────────────────────────
    // My Appointments — any logged-in user; only their own bookings.
    // ─────────────────────────────────────────────

    @GetMapping("/my-appointments")
    public String myAppointments(HttpSession session, Model model) {
        User currentUser = (User) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (currentUser == null) {
            return "redirect:/login?next=" + URLEncoder.encode("/my-appointments", StandardCharsets.UTF_8);
        }

        List<Appointment> appointments =
            appointmentService.getAppointmentsByEmail(currentUser.getEmail());

        log.info("[BookingController] GET /my-appointments user={} count={}",
            currentUser.getEmail(), appointments.size());

        model.addAttribute("appointments", appointments);
        return "my-appointments";
    }

    // ─────────────────────────────────────────────
    // Cancel Appointment
    // ─────────────────────────────────────────────

    @PostMapping("/appointments/{id}/cancel")
    public String cancelAppointment(
        @PathVariable Long id,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        User currentUser = (User) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Look up the appointment to enforce ownership for non-admins.
        Optional<Appointment> apptOpt = appointmentService.getAppointmentById(id);
        if (apptOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Appointment not found.");
            return currentUser.isAdmin() ? "redirect:/appointments" : "redirect:/my-appointments";
        }
        Appointment appt = apptOpt.get();
        if (!currentUser.isAdmin()
            && !currentUser.getEmail().equalsIgnoreCase(appt.getUserEmail())) {
            log.warn("[BookingController] User {} tried to cancel someone else's appointment id={}",
                currentUser.getEmail(), id);
            redirectAttributes.addFlashAttribute("error", "You can only cancel your own appointments.");
            return "redirect:/my-appointments";
        }

        log.info("[BookingController] POST /appointments/{}/cancel by {}", id, currentUser.getEmail());
        try {
            appointmentService.cancelAppointment(id);
            redirectAttributes.addFlashAttribute("success", "Appointment cancelled successfully.");
        } catch (Exception e) {
            log.error("[BookingController] Cancel failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to cancel: " + e.getMessage());
        }
        return currentUser.isAdmin() ? "redirect:/appointments" : "redirect:/my-appointments";
    }
}
