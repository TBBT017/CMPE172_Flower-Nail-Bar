# Flower Nail Bar – Online Appointment Scheduling System
**CMPE 172 Term Project | Spring 2026**

---

## Quick Start

### Run the app
```bash
cd flower-nail-bar
mvn spring-boot:run
```
Then open: **http://localhost:8080**

**Default admin login:** username `tran1` / password `pass1234`

---

## Project Structure

```
src/main/java/com/flowernailbar/
├── FlowerNailBarApplication.java
├── config/
│   ├── AppConfig.java                  # RestTemplate bean
│   ├── CurrentUserAdvice.java          # Injects current user into all templates
│   └── DatabaseInitializer.java        # Schema creation + seed data on startup
├── model/
│   ├── Appointment.java
│   ├── AvailabilitySlot.java
│   ├── BookingRequest.java
│   ├── Service.java
│   ├── Technician.java
│   └── User.java
├── repository/                         # Raw JDBC — no ORM
│   ├── AppointmentRepository.java
│   ├── AvailabilitySlotRepository.java # Optimistic locking via version column
│   ├── ServiceRepository.java
│   ├── TechnicianRepository.java
│   └── UserRepository.java
├── service/
│   ├── AppointmentService.java         # Core booking logic + @Transactional
│   ├── AuthService.java                # Registration + login
│   ├── SlotService.java                # Slot + service + technician queries
│   └── ConcurrentBookingException.java # Thrown on double-booking conflict
└── controller/
    ├── HomeController.java             # GET /
    ├── AuthController.java             # Login, logout, register
    ├── BookingController.java          # Multi-step booking flow (main UI)
    ├── AppointmentController.java      # REST API endpoints (M5)
    ├── NotificationController.java     # Mock external notification service (M5)
    └── HealthController.java           # Health check + metrics (M6)
```

---

## Key URLs

### Booking Flow
| URL | Description |
|-----|-------------|
| `GET  /` | Home page |
| `GET  /book?location=San+Jose` | Step 1 — Select service |
| `GET  /book/technician?serviceId=1&location=San+Jose` | Step 2 — Select technician |
| `GET  /book/slots?serviceId=1&technicianId=1&location=San+Jose` | Step 3 — Pick date + time |
| `GET  /book/confirm?serviceId=1&slotId=5&technicianId=1` | Step 4 — Confirm booking (requires login) |
| `POST /book/confirm` | Submit booking |
| `GET  /book/confirmation/{id}` | Confirmation page |

### Appointments
| URL | Description |
|-----|-------------|
| `GET  /appointments` | All appointments — admin only |
| `GET  /my-appointments` | Current user's own appointments |
| `POST /appointments/{id}/cancel` | Cancel an appointment |

### Auth
| URL | Description |
|-----|-------------|
| `GET  /login` | Login page |
| `POST /login` | Submit login |
| `GET  /register` | Register page |
| `POST /register` | Submit registration |
| `GET  /logout` | Logout |

### REST API + System
| URL | Description |
|-----|-------------|
| `POST /appointments/bookAppointment` | REST API booking endpoint (M5) |
| `GET  /appointments/list` | REST — list all appointments |
| `POST /notification/send` | Mock notification service (M5) |
| `GET  /notification/status` | Notification service health |
| `GET  /api/health` | System health check (M6) |
| `GET  /api/metrics` | Booking metrics (M6) |

---

## Architecture

```
Client (Browser)
      │
      ▼
[BookingController / AppointmentController / AuthController]   ← Spring MVC
      │
      ▼
[AppointmentService / SlotService / AuthService]               ← Business Logic + @Transactional
      │
      ▼
[AppointmentRepository / AvailabilitySlotRepository / ...]     ← Raw JDBC (no ORM)
      │
      ▼
[SQLite Database — flowernailbar.db]

      + REST call via RestTemplate (M5 distribution boundary):
[AppointmentService] ──POST /notification/send──▶ [NotificationController]
                                                    (Mock External Service)
```

---

## Concurrency Control (M4)

Double-booking is prevented using two layers:

**1. Application layer — technician conflict check:**
```java
// Before inserting, verify this technician is not already booked at this slot
boolean taken = appointmentRepo.existsByTechnicianAndSlot(technicianId, slotId);
if (taken) throw new ConcurrentBookingException("Technician unavailable.");
```

**2. Database layer — optimistic locking via version column:**
```sql
UPDATE availability_slots
   SET is_booked = 1, version = version + 1
 WHERE id = ? AND version = ? AND is_booked = 0
```

If two requests race to book the same slot:
- First one: `version = 0` matches → UPDATE affects 1 row → booking succeeds
- Second one: `version = 0` no longer matches → 0 rows affected → `ConcurrentBookingException` thrown → transaction rolls back

The `bookAppointment()` method uses `@Transactional(isolation = Isolation.SERIALIZABLE)`.

---

## Distribution Boundary (M5)

After a successful booking, `AppointmentService` calls the mock notification service via `RestTemplate` — a coarse-grained HTTP call that crosses the distribution boundary:

```
Client → POST /book/confirm
              │
              ├── Books appointment (@Transactional — commits)
              │
              └── POST /notification/send  ← HTTP call via RestTemplate
                        │
                        └── NotificationController (mock external service)
                            logs confirmation, returns "Confirmation sent!"
```

Notification failure does **not** roll back the booking — it is called outside the transaction scope and failures are logged as WARN.

---

## Health & Metrics (M6)

- `GET /api/health` — returns system status, DB connectivity, notification service status, timestamp
- `GET /api/metrics` — returns confirmed/cancelled counts, bookings last hour, available slots, slot utilization %

---

## Database Schema

```sql
users               (id, full_name, email UNIQUE, phone, password_hash, role, created_at)
services            (id, name, duration_min, price, location)
technicians         (id, name, specialty, location)
availability_slots  (id, slot_date, slot_time, location, is_booked, version)
appointments        (id, user_id, service_id, slot_id, technician_id, status, booked_at)
```

- `availability_slots.version` — optimistic lock counter, incremented on each booking/cancellation
- `appointments.status` — `'CONFIRMED'` or `'CANCELLED'`
- `users.role` — `'USER'` or `'ADMIN'`

Database file: `flowernailbar.db` (created automatically on first run)

---

## Tech Stack

- **Java 17** + **Spring Boot 3.2**
- **Spring JDBC** (raw SQL via JdbcTemplate — no ORM/Hibernate/JPA)
- **SQLite** via `sqlite-jdbc`
- **Thymeleaf** server-side templates
- **Spring Security** + BCrypt password hashing
- **RestTemplate** for mock external service call
- **SLF4J + Logback** for structured logging
