package com.sis.service;

import com.sis.dto.AllocateHostelRequest;
import com.sis.dto.CreateHostelRoomRequest;
import com.sis.dto.HostelAllocationDto;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccommodationService {
    private final HostelRoomRepository rooms;
    private final HostelAllocationRepository allocations;
    private final HostelApplicationRepository applications;
    private final StudentRepository students;

    public AccommodationService(HostelRoomRepository rooms, HostelAllocationRepository allocations,
                                 HostelApplicationRepository applications, StudentRepository students) {
        this.rooms = rooms;
        this.allocations = allocations;
        this.applications = applications;
        this.students = students;
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

    @Transactional(readOnly = true)
    public List<HostelApplication> pendingApplications() {
        return applications.findByStatusOrderByCreatedAtAsc("OPEN");
    }

    public HostelAllocation allocate(AllocateHostelRequest request) {
        HostelApplication application = applications.findByIdAndStatusForUpdate(request.getApplicationId(), "OPEN")
                .orElseThrow(() -> new IllegalStateException("Open hostel application not found"));
        students.findByIdForUpdate(application.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found for hostel application"));
        if (allocations.existsByStudentIdAndStatus(application.getStudentId(), "ACTIVE")) {
            throw new IllegalStateException("Student already has an active hostel allocation");
        }
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
        applications.findByStudentIdAndStatus(application.getStudentId(), "OPEN").forEach(otherApplication -> {
            otherApplication.setStatus("CLOSED");
            applications.save(otherApplication);
        });
        return allocations.save(allocation);
    }

    @Transactional(readOnly = true)
    public List<HostelAllocationDto> studentAllocations(Long studentId) {
        List<HostelAllocation> studentAllocations = allocations.findByStudentIdOrderByAllocatedAtDesc(studentId);
        Map<Long, HostelRoom> roomById = rooms.findAllById(studentAllocations.stream()
                        .map(HostelAllocation::getHostelRoomId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(HostelRoom::getId, room -> room));
        return studentAllocations.stream().map(allocation -> {
            HostelRoom room = roomById.get(allocation.getHostelRoomId());
            return new HostelAllocationDto(allocation.getId(), allocation.getApplicationId(), allocation.getStatus(),
                    allocation.getAllocatedAt(), room == null ? null : room.getRoomCode(),
                    room == null ? null : room.getBuilding());
        }).collect(Collectors.toList());
    }
}