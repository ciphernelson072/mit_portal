package com.sis.service;

import com.sis.dto.AllocateHostelRequest;
import com.sis.entity.HostelApplication;
import com.sis.entity.HostelRoom;
import com.sis.repository.HostelAllocationRepository;
import com.sis.repository.HostelApplicationRepository;
import com.sis.repository.HostelRoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

        HostelApplication firstApplication = applications.saveAndFlush(new HostelApplication(501L, "First"));
        HostelApplication secondApplication = applications.saveAndFlush(new HostelApplication(502L, "Second"));

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
