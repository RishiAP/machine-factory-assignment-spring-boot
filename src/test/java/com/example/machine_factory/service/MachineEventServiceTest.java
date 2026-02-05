package com.example.machine_factory.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.machine_factory.dto.MachineEventsBatchResponse;
import com.example.machine_factory.dto.MachineStatsResponse;
import com.example.machine_factory.dto.RejectionReason;
import com.example.machine_factory.entity.MachineEvent;
import com.example.machine_factory.repository.MachineEventRepository;

@SpringBootTest
class MachineEventServiceTest {

    @Autowired
    private MachineEventService machineEventService;

    @Autowired
    private MachineStatsService machineStatsService;

    @Autowired
    private MachineEventRepository machineEventRepository;

    @BeforeEach
    void setUp() {
        machineEventRepository.deleteAll();
    }

    // ================== TEST 1: Identical duplicate eventId → deduped ==================
    @Test
    void testIdenticalDuplicateEventIdIsDeduped() {
        // Truncate to microseconds to match database precision
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        
        // Use unique ID with timestamp to avoid conflicts
        String uniqueId = "E-DEDUP-" + System.currentTimeMillis();
        
        MachineEvent event1 = new MachineEvent(
            uniqueId, eventTime, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        // First batch
        MachineEventsBatchResponse response1 = machineEventService.saveBatch(List.of(event1));
        
        // Verify event was saved
        Optional<MachineEvent> saved = machineEventRepository.findById(uniqueId);
        assertTrue(saved.isPresent(), "Event should be saved");
        
        assertEquals(0, response1.getDeduped());
        
        // Send identical event again (same eventId + identical payload)
        MachineEvent event2 = new MachineEvent(
            uniqueId, eventTime, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response2 = machineEventService.saveBatch(List.of(event2));
        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getDeduped());
        
        // Verify still only one record exists for this ID
        assertTrue(machineEventRepository.findById(uniqueId).isPresent(), "Event should still exist");
    }

    // ================== TEST 2: Different payload + newer receivedTime → update happens ==================
    @Test
    void testDifferentPayloadWithNewerReceivedTimeUpdates() {
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        MachineEvent originalEvent = new MachineEvent(
            "E-UPDATE-1", eventTime, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response1 = machineEventService.saveBatch(List.of(originalEvent));
        assertEquals(1, response1.getAccepted());
        
        // Verify original values
        Optional<MachineEvent> savedOpt = machineEventRepository.findById("E-UPDATE-1");
        assertTrue(savedOpt.isPresent());
        assertEquals(2, savedOpt.get().getDefectCount());
        Instant firstReceivedTime = savedOpt.get().getReceivedTime();
        
        // Small delay to ensure receivedTime is newer
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        // Send event with different payload (different defectCount)
        MachineEvent updatedEvent = new MachineEvent(
            "E-UPDATE-1", eventTime, null, "M-001", 1000, 5, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response2 = machineEventService.saveBatch(List.of(updatedEvent));
        assertEquals(0, response2.getAccepted());
        assertEquals(1, response2.getUpdated());
        
        // Verify the update happened
        Optional<MachineEvent> updatedOpt = machineEventRepository.findById("E-UPDATE-1");
        assertTrue(updatedOpt.isPresent());
        assertEquals(5, updatedOpt.get().getDefectCount()); // Updated value
        assertTrue(updatedOpt.get().getReceivedTime().isAfter(firstReceivedTime));
    }

    // ================== TEST 3: Different payload + older receivedTime → ignored ==================
    @Test
    void testDifferentPayloadWithOlderReceivedTimeIsIgnored() {
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        Instant olderReceivedTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(2, ChronoUnit.HOURS);
        
        // First, insert an event
        MachineEvent originalEvent = new MachineEvent(
            "E-IGNORE-1", eventTime, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        machineEventService.saveBatch(List.of(originalEvent));
        
        Optional<MachineEvent> savedOpt = machineEventRepository.findById("E-IGNORE-1");
        assertTrue(savedOpt.isPresent());
        Integer originalDefectCount = savedOpt.get().getDefectCount();
        
        // Send event with older receivedTime and different payload
        MachineEvent olderEvent = new MachineEvent(
            "E-IGNORE-1", eventTime, olderReceivedTime, "M-001", 1000, 99, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(List.of(olderEvent));
        // Event should be removed from validEventsMap due to older receivedTime
        assertEquals(0, response.getUpdated());
        
        // Verify original value is preserved
        Optional<MachineEvent> afterOpt = machineEventRepository.findById("E-IGNORE-1");
        assertTrue(afterOpt.isPresent());
        assertEquals(originalDefectCount, afterOpt.get().getDefectCount());
    }

    // ================== TEST 4: Invalid duration rejected ==================
    @Test
    void testInvalidDurationIsRejected() {
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        // Negative duration
        MachineEvent negativeEvent = new MachineEvent(
            "E-NEG-DUR", eventTime, null, "M-001", -100, 2, "L-01", "F-01"
        );
        
        // Duration > 6 hours (21600000 ms)
        MachineEvent tooLongEvent = new MachineEvent(
            "E-LONG-DUR", eventTime, null, "M-001", 21600001, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(List.of(negativeEvent, tooLongEvent));
        
        assertEquals(0, response.getAccepted());
        assertEquals(2, response.getRejected());
        assertEquals(2, response.getRejections().size());
        
        assertTrue(response.getRejections().stream()
            .allMatch(r -> r.getReason() == RejectionReason.INVALID_DURATION));
        
        // Verify nothing was saved
        assertEquals(0, machineEventRepository.count());
    }

    // ================== TEST 5: Future eventTime rejected ==================
    @Test
    void testFutureEventTimeIsRejected() {
        // Event time more than 15 minutes in the future
        Instant futureTime = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(20, ChronoUnit.MINUTES);
        
        MachineEvent futureEvent = new MachineEvent(
            "E-FUTURE", futureTime, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(List.of(futureEvent));
        
        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertEquals(RejectionReason.FUTURE_EVENT_TIME, response.getRejections().get(0).getReason());
        
        // Verify nothing was saved
        assertEquals(0, machineEventRepository.count());
    }

    // ================== TEST 6: defectCount = -1 ignored in defect totals ==================
    @Test
    void testDefectCountMinusOneIgnoredInTotals() {
        Instant baseTime = Instant.parse("2026-01-15T10:00:00Z").truncatedTo(ChronoUnit.MICROS);
        
        // Event with normal defect count
        MachineEvent normalEvent = new MachineEvent(
            "E-NORMAL", baseTime, null, "M-001", 1000, 5, "L-01", "F-01"
        );
        
        // Event with defectCount = -1 (unknown)
        MachineEvent unknownDefectEvent = new MachineEvent(
            "E-UNKNOWN", baseTime.plus(1, ChronoUnit.HOURS), null, "M-001", 1500, -1, "L-01", "F-01"
        );
        
        // Event with zero defects
        MachineEvent zeroDefectEvent = new MachineEvent(
            "E-ZERO", baseTime.plus(2, ChronoUnit.HOURS), null, "M-001", 2000, 0, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(
            List.of(normalEvent, unknownDefectEvent, zeroDefectEvent)
        );
        
        assertEquals(3, response.getAccepted());
        
        // Query stats for the window
        MachineStatsResponse stats = machineStatsService.getMachineStats(
            "M-001",
            baseTime,
            baseTime.plus(4, ChronoUnit.HOURS)
        );
        
        assertEquals(3, stats.getEventsCount()); // All 3 events counted
        assertEquals(5, stats.getDefectsCount()); // Only 5 from normal + 0 from zero (defectCount=-1 stored as null)
    }

    // ================== TEST 7: start/end boundary correctness (inclusive/exclusive) ==================
    @Test
    void testStartEndBoundaryCorrectness() {
        Instant start = Instant.parse("2026-01-15T10:00:00Z").truncatedTo(ChronoUnit.MICROS);
        Instant end = Instant.parse("2026-01-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
        
        // Event exactly at start (should be included)
        MachineEvent atStart = new MachineEvent(
            "E-START", start, null, "M-001", 1000, 1, "L-01", "F-01"
        );
        
        // Event in middle (should be included)
        MachineEvent middle = new MachineEvent(
            "E-MIDDLE", start.plus(1, ChronoUnit.HOURS), null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        // Event exactly at end (should be EXCLUDED)
        MachineEvent atEnd = new MachineEvent(
            "E-END", end, null, "M-001", 1000, 4, "L-01", "F-01"
        );
        
        // Event before start (should be excluded)
        MachineEvent beforeStart = new MachineEvent(
            "E-BEFORE", start.minus(1, ChronoUnit.SECONDS), null, "M-001", 1000, 8, "L-01", "F-01"
        );
        
        // Event after end (should be excluded)
        MachineEvent afterEnd = new MachineEvent(
            "E-AFTER", end.plus(1, ChronoUnit.SECONDS), null, "M-001", 1000, 16, "L-01", "F-01"
        );
        
        machineEventService.saveBatch(List.of(atStart, middle, atEnd, beforeStart, afterEnd));
        
        MachineStatsResponse stats = machineStatsService.getMachineStats("M-001", start, end);
        
        // Only atStart (1) and middle (2) should be included
        assertEquals(2, stats.getEventsCount());
        assertEquals(3, stats.getDefectsCount()); // 1 + 2 = 3
    }

    // ================== TEST 8: Thread-safety test ==================
    @Test
    void testConcurrentIngestionDoesNotCorruptData() throws InterruptedException {
        int numThreads = 10;
        int eventsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger totalAccepted = new AtomicInteger(0);
        AtomicInteger totalRejected = new AtomicInteger(0);
        
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    List<MachineEvent> events = new ArrayList<>();
                    for (int i = 0; i < eventsPerThread; i++) {
                        String eventId = "E-T" + threadId + "-" + i;
                        events.add(new MachineEvent(
                            eventId, 
                            baseTime.plusSeconds(i), 
                            null, 
                            "M-00" + (threadId % 5), 
                            1000 + i, 
                            i % 10, 
                            "L-0" + (threadId % 3), 
                            "F-01"
                        ));
                    }
                    
                    MachineEventsBatchResponse response = machineEventService.saveBatch(events);
                    totalAccepted.addAndGet(response.getAccepted());
                    totalRejected.addAndGet(response.getRejected());
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Test timed out");
        executor.shutdown();
        
        // Verify data integrity
        long dbCount = machineEventRepository.count();
        int expectedTotal = numThreads * eventsPerThread;
        
        assertEquals(expectedTotal, totalAccepted.get() + totalRejected.get(), 
            "Total processed should equal total submitted");
        assertEquals(expectedTotal, dbCount, 
            "Database should contain all accepted events");
        
        // Verify no data corruption by checking a few random records
        for (int t = 0; t < numThreads; t++) {
            String eventId = "E-T" + t + "-50";
            Optional<MachineEvent> event = machineEventRepository.findById(eventId);
            assertTrue(event.isPresent(), "Event " + eventId + " should exist");
            assertEquals(1050, event.get().getDurationMs(), "Duration should be correct");
        }
    }

    // ================== Additional Test: Mixed batch with valid, invalid, and duplicates ==================
    @Test
    void testMixedBatchProcessing() {
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        List<MachineEvent> batch = List.of(
            // Valid event
            new MachineEvent("E-VALID-1", eventTime, null, "M-001", 1000, 2, "L-01", "F-01"),
            // Valid event
            new MachineEvent("E-VALID-2", eventTime, null, "M-001", 2000, 3, "L-01", "F-01"),
            // Invalid duration (negative)
            new MachineEvent("E-INVALID-1", eventTime, null, "M-001", -100, 1, "L-01", "F-01"),
            // Invalid duration (too long)
            new MachineEvent("E-INVALID-2", eventTime, null, "M-001", 30000000, 1, "L-01", "F-01"),
            // Future event
            new MachineEvent("E-FUTURE-1", Instant.now().truncatedTo(ChronoUnit.MICROS).plus(30, ChronoUnit.MINUTES), null, "M-001", 1000, 1, "L-01", "F-01"),
            // Valid event with defectCount -1
            new MachineEvent("E-VALID-3", eventTime, null, "M-001", 3000, -1, "L-01", "F-01")
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(batch);
        
        assertEquals(3, response.getAccepted()); // E-VALID-1, E-VALID-2, E-VALID-3
        assertEquals(3, response.getRejected()); // E-INVALID-1, E-INVALID-2, E-FUTURE-1
        assertEquals(0, response.getDeduped());
        assertEquals(0, response.getUpdated());
        
        // Verify rejection reasons
        assertEquals(2, response.getRejections().stream()
            .filter(r -> r.getReason() == RejectionReason.INVALID_DURATION).count());
        assertEquals(1, response.getRejections().stream()
            .filter(r -> r.getReason() == RejectionReason.FUTURE_EVENT_TIME).count());
        
        // Verify database state
        assertEquals(3, machineEventRepository.count());
        
        // Verify defectCount = -1 stored as null
        Optional<MachineEvent> valid3 = machineEventRepository.findById("E-VALID-3");
        assertTrue(valid3.isPresent());
        assertNull(valid3.get().getDefectCount());
    }

    // ================== PERFORMANCE TEST: 1000 events in under 1 second ==================
    @Test
    void testPerformance1000EventsUnder1Second() {
        List<MachineEvent> events = new ArrayList<>();
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        // Generate 1000 unique events
        for (int i = 0; i < 1000; i++) {
            events.add(new MachineEvent(
                "E-PERF-" + i, 
                baseTime.plusSeconds(i), 
                null, 
                "M-001", 
                1000 + i, 
                i % 10, 
                "L-01", 
                "F-01"
            ));
        }
        
        // Measure time
        long startTime = System.currentTimeMillis();
        MachineEventsBatchResponse response = machineEventService.saveBatch(events);
        long endTime = System.currentTimeMillis();
        
        long timeTaken = endTime - startTime;
        
        System.out.println("========================================");
        System.out.println("PERFORMANCE BENCHMARK RESULTS");
        System.out.println("========================================");
        System.out.println("Events processed: 1000");
        System.out.println("Time taken: " + timeTaken + " ms");
        System.out.println("Accepted: " + response.getAccepted());
        System.out.println("Rejected: " + response.getRejected());
        System.out.println("========================================");
        
        assertEquals(1000, response.getAccepted());
        assertTrue(timeTaken < 1000, 
            "Should process 1000 events in under 1 second. Took: " + timeTaken + "ms");
    }

    // ================== Test: Stats status calculation ==================
    @Test
    void testStatsStatusCalculation() {
        Instant start = Instant.parse("2026-01-15T10:00:00Z").truncatedTo(ChronoUnit.MICROS);
        Instant end = Instant.parse("2026-01-15T16:00:00Z").truncatedTo(ChronoUnit.MICROS); // 6 hour window
        
        // For Healthy: avgDefectRate < 2.0, so defects < 12 for 6 hours
        MachineEvent healthyEvent = new MachineEvent(
            "E-HEALTHY", start.plus(1, ChronoUnit.HOURS), null, "M-HEALTHY", 1000, 5, "L-01", "F-01"
        );
        
        machineEventService.saveBatch(List.of(healthyEvent));
        
        MachineStatsResponse healthyStats = machineStatsService.getMachineStats("M-HEALTHY", start, end);
        assertEquals("Healthy", healthyStats.getStatus());
        assertTrue(healthyStats.getAvgDefectRate() < 2.0);
        
        // For Warning: avgDefectRate >= 2.0, so defects >= 12 for 6 hours
        MachineEvent warningEvent = new MachineEvent(
            "E-WARNING", start.plus(2, ChronoUnit.HOURS), null, "M-WARNING", 1000, 15, "L-01", "F-01"
        );
        
        machineEventService.saveBatch(List.of(warningEvent));
        
        MachineStatsResponse warningStats = machineStatsService.getMachineStats("M-WARNING", start, end);
        assertEquals("Warning", warningStats.getStatus());
        assertTrue(warningStats.getAvgDefectRate() >= 2.0);
    }

    // ================== Test: Duration boundary values ==================
    @Test
    void testDurationBoundaryValues() {
        Instant eventTime = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        
        // Duration exactly 0 (should be accepted)
        MachineEvent zeroDuration = new MachineEvent(
            "E-ZERO-DUR", eventTime, null, "M-001", 0, 2, "L-01", "F-01"
        );
        
        // Duration exactly 6 hours in ms (21600000) (should be accepted)
        MachineEvent maxDuration = new MachineEvent(
            "E-MAX-DUR", eventTime, null, "M-001", 21600000, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(List.of(zeroDuration, maxDuration));
        
        assertEquals(2, response.getAccepted());
        assertEquals(0, response.getRejected());
    }

    // ================== Test: Event time within 15 min future is accepted ==================
    @Test
    void testEventTimeWithin15MinFutureIsAccepted() {
        // Event time exactly 10 minutes in the future (should be accepted)
        Instant nearFuture = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(10, ChronoUnit.MINUTES);
        
        MachineEvent nearFutureEvent = new MachineEvent(
            "E-NEAR-FUTURE", nearFuture, null, "M-001", 1000, 2, "L-01", "F-01"
        );
        
        MachineEventsBatchResponse response = machineEventService.saveBatch(List.of(nearFutureEvent));
        
        assertEquals(1, response.getAccepted());
        assertEquals(0, response.getRejected());
        assertEquals(1, machineEventRepository.count());
    }
}
