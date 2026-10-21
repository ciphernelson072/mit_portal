package com.sis.service;

import com.sis.dto.AllocateHostelRequest;
import com.sis.dto.CreateHostelRoomRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AccommodationService {
    private final HostelRoomRepository rooms;
    private final HostelAllocationRepository allocations;
    private final HostelApplicationRepository applications;

    public AccommodationService(HostelRoomRepository rooms, HostelAllocationRepository allocations,
                                 HostelApplicationRepository applications) {
        this.rooms = rooms;
        this.allocations = allocations;
        this.applications = applications;
    }

    public HostelRoom createRoom(CreateHostelRoomRequest request) {
        HostelRoom room = new HostelRoom();
        room.setRoomCode(request.getRoomCode());
        room.setBuilding(request.getBuilding());
        room.setCapacity(request.getCapacity());
        room.setAvailableBeds(request.getCapacity());
        return rooms.save(room);
    }

    @Transactional(readOnly = true)
    public List<HostelRoom> rooms() { return rooms.findByActiveTrueOrderByBuildingAscRoomCodeAsc(); }

    public HostelAllocation allocate(AllocateHostelRequest request) {
        HostelApplication application = applications.findByIdAndStatusForUpdate(request.getApplicationId(), "OPEN")
                .orElseThrow(() -> new IllegalStateException("Open hostel application not found"));
        HostelRoom room = rooms.findActiveByIdForUpdate(request.getHostelRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Active hostel room not found"));
        if (room.getAvailableBeds() < 1) throw new IllegalStateException("Hostel room has no available beds");
        room.setAvailableBeds(room.getAvailableBeds() - 1);
        rooms.save(room);
        HostelAllocation allocation = new HostelAllocation();
        allocation.setHostelRoomId(room.getId());
        allocation.setStudentId(application.getStudentId());
        allocation.setApplicationId(application.getId());
        application.setStatus("ALLOCATED");
        applications.save(application);
        return allocations.save(allocation);
    }

    @Transactional(readOnly = true)
    public List<HostelAllocation> studentAllocations(Long studentId) { return allocations.findByStudentIdOrderByAllocatedAtDesc(studentId); }
}