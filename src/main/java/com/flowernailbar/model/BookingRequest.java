package com.flowernailbar.model;

/**
 * BookingRequest — Data Transfer Object for the appointment booking form.
 * Carries all fields submitted from the "Confirm Appointment" page.
 */
public class BookingRequest {
    private Long serviceId;
    private Long slotId;
    private Long technicianId;
    private String fullName;
    private String email;
    private String phone;

    public BookingRequest() {}

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public Long getTechnicianId() { return technicianId; }
    public void setTechnicianId(Long technicianId) { this.technicianId = technicianId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
