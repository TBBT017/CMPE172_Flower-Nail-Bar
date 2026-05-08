package com.flowernailbar.model;

public class AvailabilitySlot {
    private Long id;
    private String slotDate;
    private String slotTime;
    private String location;
    private boolean isBooked;
    private int version; // for optimistic locking (concurrency control)

    public AvailabilitySlot() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlotDate() { return slotDate; }
    public void setSlotDate(String slotDate) { this.slotDate = slotDate; }

    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isBooked() { return isBooked; }
    public void setBooked(boolean booked) { isBooked = booked; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
