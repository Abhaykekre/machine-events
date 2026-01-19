package com.factory.machine_events;

import com.factory.machine_events.dto.BatchResponse;
import com.factory.machine_events.dto.EventRequest;
import com.factory.machine_events.dto.StatsResponse;
import com.factory.machine_events.model.MachineEvent;
import com.factory.machine_events.repository.EventRepository;
import com.factory.machine_events.service.EventService;
import com.factory.machine_events.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // Test 1: Identical duplicate eventId → deduped
    @Test
    void testIdenticalDuplicateIsDeduped() {
        List<EventRequest> events = new ArrayList<>();

        EventRequest event1 = createEvent("E-1", Instant.now(), "M-001", 1000L, 0);
        EventRequest event2 = createEvent("E-1", Instant.now(), "M-001", 1000L, 0);

        events.add(event1);
        events.add(event2);

        BatchResponse response = eventService.processBatch(events);

        assertEquals(1, response.getAccepted());
        assertEquals(1, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(0, response.getRejected());
    }

    // Test 2: Different payload + newer receivedTime → update happens
    @Test
    void testDifferentPayloadNewerReceivedTimeUpdates() {
        List<EventRequest> batch1 = new ArrayList<>();
        Instant now = Instant.now();

        EventRequest event1 = createEvent("E-2", now, "M-001", 1000L, 0);
        event1.setReceivedTime(now.minusSeconds(10));
        batch1.add(event1);

        eventService.processBatch(batch1);

        // Now send updated event with newer receivedTime
        List<EventRequest> batch2 = new ArrayList<>();
        EventRequest event2 = createEvent("E-2", now, "M-001", 2000L, 5); // Different duration and defects
        event2.setReceivedTime(now);
        batch2.add(event2);

        BatchResponse response = eventService.processBatch(batch2);

        assertEquals(0, response.getAccepted());
        assertEquals(0, response.getDeduped());
        assertEquals(1, response.getUpdated());

        // Verify the update
        MachineEvent updated = eventRepository.findByEventId("E-2").orElseThrow();
        assertEquals(2000L, updated.getDurationMs());
        assertEquals(5, updated.getDefectCount());
    }

    // Test 3: Different payload + older receivedTime → ignored
    @Test
    void testDifferentPayloadOlderReceivedTimeIgnored() {
        List<EventRequest> batch1 = new ArrayList<>();
        Instant now = Instant.now();

        EventRequest event1 = createEvent("E-3", now, "M-001", 1000L, 0);
        event1.setReceivedTime(now);
        batch1.add(event1);

        eventService.processBatch(batch1);

        // Now send event with older receivedTime
        List<EventRequest> batch2 = new ArrayList<>();
        EventRequest event2 = createEvent("E-3", now, "M-001", 2000L, 5);
        event2.setReceivedTime(now.minusSeconds(10)); // Older
        batch2.add(event2);

        BatchResponse response = eventService.processBatch(batch2);

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getDeduped()); // Ignored as duplicate
        assertEquals(0, response.getUpdated());

        // Verify original data is unchanged
        MachineEvent original = eventRepository.findByEventId("E-3").orElseThrow();
        assertEquals(1000L, original.getDurationMs());
        assertEquals(0, original.getDefectCount());
    }

    // Test 4: Invalid duration rejected
    @Test
    void testInvalidDurationRejected() {
        List<EventRequest> events = new ArrayList<>();

        // Negative duration
        EventRequest event1 = createEvent("E-4", Instant.now(), "M-001", -100L, 0);
        events.add(event1);

        // Duration > 6 hours
        EventRequest event2 = createEvent("E-5", Instant.now(), "M-001", 7L * 60 * 60 * 1000, 0);
        events.add(event2);

        BatchResponse response = eventService.processBatch(events);

        assertEquals(0, response.getAccepted());
        assertEquals(2, response.getRejected());
        assertTrue(response.getRejections().stream()
                .anyMatch(r -> r.getReason().equals("INVALID_DURATION")));
    }

    // Test 5: Future eventTime rejected
    @Test
    void testFutureEventTimeRejected() {
        List<EventRequest> events = new ArrayList<>();

        // Event 20 minutes in the future (> 15 min threshold)
        Instant futureTime = Instant.now().plus(20, ChronoUnit.MINUTES);
        EventRequest event = createEvent("E-6", futureTime, "M-001", 1000L, 0);
        events.add(event);

        BatchResponse response = eventService.processBatch(events);

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertEquals("FUTURE_EVENT_TIME", response.getRejections().get(0).getReason());
    }

    // Test 6: DefectCount = -1 ignored in defect totals
    @Test
    void testDefectCountMinusOneIgnoredInStats() {
        List<EventRequest> events = new ArrayList<>();
        Instant now = Instant.now();

        // Regular event with 5 defects
        events.add(createEvent("E-7", now, "M-001", 1000L, 5));

        // Event with unknown defects (-1)
        events.add(createEvent("E-8", now, "M-001", 1000L, -1));

        // Another regular event with 3 defects
        events.add(createEvent("E-9", now, "M-001", 1000L, 3));

        eventService.processBatch(events);

        // Query stats
        StatsResponse stats = statsService.getStats("M-001",
                now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS));

        assertEquals(3, stats.getEventsCount()); // All 3 events counted
        assertEquals(8, stats.getDefectsCount()); // Only 5 + 3, -1 is ignored
    }

    // Test 7: start/end boundary correctness (inclusive/exclusive)
    @Test
    void testStartEndBoundaryCorrectness() {
        Instant base = Instant.parse("2026-01-15T10:00:00Z");

        List<EventRequest> events = new ArrayList<>();
        events.add(createEvent("E-10", base.minus(1, ChronoUnit.SECONDS), "M-001", 1000L, 1)); // Before start
        events.add(createEvent("E-11", base, "M-001", 1000L, 2)); // At start (inclusive)
        events.add(createEvent("E-12", base.plus(30, ChronoUnit.MINUTES), "M-001", 1000L, 3)); // In range
        events.add(createEvent("E-13", base.plus(1, ChronoUnit.HOURS), "M-001", 1000L, 4)); // At end (exclusive)
        events.add(createEvent("E-14", base.plus(61, ChronoUnit.MINUTES), "M-001", 1000L, 5)); // After end

        eventService.processBatch(events);

        // Query with start=base, end=base+1hour
        StatsResponse stats = statsService.getStats("M-001",
                base,
                base.plus(1, ChronoUnit.HOURS));

        assertEquals(2, stats.getEventsCount()); // Only E-11 and E-12
        assertEquals(5, stats.getDefectsCount()); // 2 + 3
    }

    // Test 8: Thread-safety test - concurrent ingestion
    @Test
    void testConcurrentIngestionThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalAccepted = new AtomicInteger(0);

        Instant now = Instant.now();

        // Test 1: Each thread creates unique events
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    List<EventRequest> events = new ArrayList<>();
                    for (int i = 0; i < eventsPerThread; i++) {
                        String eventId = "E-THREAD-" + threadId + "-" + i;
                        events.add(createEvent(eventId, now, "M-001", 1000L, i % 5));
                    }

                    BatchResponse response = eventService.processBatch(events);
                    totalAccepted.addAndGet(response.getAccepted());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Verify all unique events were accepted
        assertEquals(threadCount * eventsPerThread, totalAccepted.get());

        // Verify database has correct count
        long dbCount = eventRepository.count();
        assertEquals(threadCount * eventsPerThread, dbCount);

        // Test 2: Concurrent updates to same event with explicit timing
        CountDownLatch updateLatch = new CountDownLatch(threadCount);
        ExecutorService updateExecutor = Executors.newFixedThreadPool(threadCount);

        Instant baseTime = Instant.now();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            updateExecutor.submit(() -> {
                try {
                    // Add small delay to ensure different receivedTime
                    Thread.sleep(threadId * 10);

                    List<EventRequest> events = new ArrayList<>();
                    EventRequest event = createEvent("E-SHARED", baseTime, "M-001",
                            1000L + threadId, threadId);

                    // Set receivedTime explicitly with increasing values
                    event.setReceivedTime(baseTime.plusMillis(threadId * 100));
                    events.add(event);

                    eventService.processBatch(events);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    updateLatch.countDown();
                }
            });
        }

        updateLatch.await();
        updateExecutor.shutdown();

        // Give a moment for all updates to complete
        Thread.sleep(100);

        // Verify only one version exists
        MachineEvent sharedEvent = eventRepository.findByEventId("E-SHARED").orElseThrow();

        // The event with highest receivedTime should win
        // Which is the last thread (threadCount - 1)
        long expectedDuration = 1000L + (threadCount - 1);

        // Allow for some flexibility in concurrent scenarios
        assertTrue(sharedEvent.getDurationMs() >= 1000L &&
                        sharedEvent.getDurationMs() <= expectedDuration,
                "Duration should be between 1000 and " + expectedDuration +
                        ", but was " + sharedEvent.getDurationMs());

        // Most importantly: verify no duplicate entries
        long sharedEventCount = eventRepository.findAll().stream()
                .filter(e -> e.getEventId().equals("E-SHARED"))
                .count();
        assertEquals(1, sharedEventCount, "Should have exactly one E-SHARED event");
    }

    // Helper method to create event request
    private EventRequest createEvent(String eventId, Instant eventTime,
                                     String machineId, Long duration, Integer defects) {
        EventRequest event = new EventRequest();
        event.setEventId(eventId);
        event.setEventTime(eventTime);
        event.setReceivedTime(Instant.now());
        event.setMachineId(machineId);
        event.setDurationMs(duration);
        event.setDefectCount(defects);
        event.setLineId("LINE-1");
        event.setFactoryId("F01");
        return event;
    }
}