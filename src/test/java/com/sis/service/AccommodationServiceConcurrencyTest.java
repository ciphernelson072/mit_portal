package com.sis.service;

import com.sis.dto.AllocateHostelRequest;
import com.sis.entity.HostelApplication;
import com.sis.entity.HostelAllocation;
import com.sis.entity.Role;
import com.sis.entity.HostelRoom;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.HostelAllocationRepository;
import com.sis.repository.HostelApplicationRepository;
import com.sis.repository.HostelRoomRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(AccommodationService.class)
class AccommodationServiceConcurrencyTest {

    @Autowired
    private AccommodationService accommodation;

    @Autowired
    private HostelRoomRepository rooms;

    @Autowired
    private HostelApplicationRepository applications;

    @Autowired
    private HostelAllocationRepository allocations;

    @Autowired
    private StudentRepository students;

    @Test
    void pendingApplicationsExcludeAlreadyAllocatedApplications() {
        Student firstStudent = createStudent("hostel-pending-1");
        Student secondStudent = createStudent("hostel-pending-2");
        Student allocatedStudent = createStudent("hostel-pending-3");
        HostelApplication first = applications.saveAndFlush(new HostelApplication(firstStudent.getId(), "First"));
        HostelApplication second = applications.saveAndFlush(new HostelApplication(secondStudent.getId(), "Second"));
        HostelApplication allocated = new HostelApplication(allocatedStudent.getId(), "Allocated");
        allocated.setStatus("ALLOCATED");
        allocated = applications.saveAndFlush(allocated);

        assertThat(accommodation.pendingApplications())
                .extracting(HostelApplication::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId())
                .doesNotContain(allocated.getId());
    }

    @Test
    void studentAllocationDetailsIncludeBuildingAndRoomCode() {
        Student student = createStudent("hostel-allocation-details");
        HostelRoom room = new HostelRoom();
        room.setRoomCode("B-12");
        room.setBuilding("East Hall");
        room.setCapacity(2);
        room.setAvailableBeds(1);
        room = rooms.saveAndFlush(room);

        HostelAllocation allocation = new HostelAllocation();
        allocation.setHostelRoomId(room.getId());
        allocation.setStudentId(student.getId());
        allocation.setApplicationId(601L);
        allocations.saveAndFlush(allocation);

        assertThat(accommodation.studentAllocations(student.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getBuilding()).isEqualTo("East Hall");
                    assertThat(item.getRoomCode()).isEqualTo("B-12");
                    assertThat(item.getStatus()).isEqualTo("ACTIVE");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAllocationsCannotOverbookLastBed() throws Exception {
        HostelRoom room = new HostelRoom();
        room.setRoomCode("RACE-1");
        room.setBuilding("Concurrency Hall");
        room.setCapacity(1);
        room.setAvailableBeds(1);
        room = rooms.saveAndFlush(room);
        Long roomId = room.getId();

        Student firstStudent = students.saveAndFlush(student("hostel-race-1"));
        Student secondStudent = students.saveAndFlush(student("hostel-race-2"));
        HostelApplication firstApplication = applications.saveAndFlush(new HostelApplication(firstStudent.getId(), "First"));
        HostelApplication secondApplication = applications.saveAndFlush(new HostelApplication(secondStudent.getId(), "Second"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> allocateWhenReleased(
                    ready, start, firstApplication.getId(), roomId));
            Future<Boolean> second = executor.submit(() -> allocateWhenReleased(
                    ready, start, secondApplication.getId(), roomId));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulAllocations = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successfulAllocations).isEqualTo(1);
            assertThat(rooms.findById(roomId).orElseThrow().getAvailableBeds()).isZero();
            assertThat(allocations.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void studentCannotReceiveMultipleActiveAllocations() {
        Student student = createStudent("hostel-one-active");
        HostelRoom room = saveRoom("ONE-ACTIVE", 2);
        HostelApplication firstApplication = applications.saveAndFlush(new HostelApplication(student.getId(), "First"));
        HostelApplication secondApplication = applications.saveAndFlush(new HostelApplication(student.getId(), "Second"));

        assertThat(accommodation.allocate(request(firstApplication.getId(), room.getId()))).isNotNull();
        assertThat(applications.findById(secondApplication.getId()).orElseThrow().getStatus()).isEqualTo("CLOSED");
        assertThat(accommodation.pendingApplications())
                .extracting(HostelApplication::getId)
                .doesNotContain(secondApplication.getId());
        assertThatThrownBy(() -> accommodation.allocate(request(secondApplication.getId(), room.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Open hostel application not found");
        assertThat(rooms.findById(room.getId()).orElseThrow().getAvailableBeds()).isEqualTo(1);
        assertThat(allocations.findByStudentIdOrderByAllocatedAtDesc(student.getId())).hasSize(1);
    }

    private HostelRoom saveRoom(String roomCode, int capacity) {
        HostelRoom room = new HostelRoom();
        room.setRoomCode(roomCode);
        room.setBuilding("Test Hall");
        room.setCapacity(capacity);
        room.setAvailableBeds(capacity);
        return rooms.saveAndFlush(room);
    }

    private static AllocateHostelRequest request(Long applicationId, Long roomId) {
        AllocateHostelRequest request = new AllocateHostelRequest();
        request.setApplicationId(applicationId);
        request.setHostelRoomId(roomId);
        return request;
    }

    private Student createStudent(String username) {
        return students.saveAndFlush(student(username));
    }

    private static Student student(String username) {
        return new Student(new User(username, "encoded", Role.STUDENT), username, "Year 1");
    }

    private boolean allocateWhenReleased(CountDownLatch ready, CountDownLatch start,
                                         Long applicationId, Long roomId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Allocation concurrency test did not start");
        }
        AllocateHostelRequest request = new AllocateHostelRequest();
        request.setApplicationId(applicationId);
        request.setHostelRoomId(roomId);
        try {
            accommodation.allocate(request);
            return true;
        } catch (IllegalStateException expectedCapacityConflict) {
            return false;
        }
    }
}
