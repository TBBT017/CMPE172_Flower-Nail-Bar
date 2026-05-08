package com.flowernailbar.service;

import com.flowernailbar.model.AvailabilitySlot;
import com.flowernailbar.model.Service;
import com.flowernailbar.model.Technician;
import com.flowernailbar.repository.AvailabilitySlotRepository;
import com.flowernailbar.repository.ServiceRepository;
import com.flowernailbar.repository.TechnicianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

/**
 * SlotService — handles queries for available time slots and services.
 */
@org.springframework.stereotype.Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);

    @Autowired
    private AvailabilitySlotRepository slotRepo;

    @Autowired
    private ServiceRepository serviceRepo;

    @Autowired
    private TechnicianRepository technicianRepo;

    public List<Service> getServicesByLocation(String location) {
        return serviceRepo.findByLocation(location);
    }

    public List<Service> getAllServices() {
        return serviceRepo.findAll();
    }

    public Optional<Service> getServiceById(Long id) {
        return serviceRepo.findById(id);
    }

    public List<String> getAvailableDates(String location) {
        return slotRepo.findAvailableDatesByLocation(location);
    }

    public List<AvailabilitySlot> getAvailableSlots(String date, String location) {
        log.info("[SlotService] Querying slots for date={} location={}", date, location);
        return slotRepo.findAvailableByDateAndLocation(date, location);
    }

    /**
     * Get all slots for the date/location, marking a slot as booked only if
     * the given technician is already booked at that time.
     */
    public List<AvailabilitySlot> getAllSlotsForDateAndTechnician(String date, String location, Long technicianId) {
        log.info("[SlotService] Querying slots for date={} location={} technicianId={}", date, location, technicianId);
        return slotRepo.findAllByDateAndLocationForTechnician(date, location, technicianId);
    }

    public Optional<AvailabilitySlot> getSlotById(Long id) {
        return slotRepo.findById(id);
    }

    public List<Technician> getTechniciansByLocation(String location) {
        return technicianRepo.findByLocation(location);
    }

    public Optional<Technician> getTechnicianById(Long id) {
        return technicianRepo.findById(id);
    }
}
