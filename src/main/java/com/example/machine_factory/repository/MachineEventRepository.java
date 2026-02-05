package com.example.machine_factory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.machine_factory.dto.LineStatsResponse;
import com.example.machine_factory.dto.MachineStatsQueryResult;
import com.example.machine_factory.entity.MachineEvent;

@Repository
public interface MachineEventRepository extends JpaRepository<MachineEvent, String> {
    @Query("SELECT e FROM MachineEvent e WHERE e.eventId = :eventId")
    Optional<MachineEvent> findByEventId(String eventId);

    @Query("SELECT COUNT(e) AS eventsCount, SUM(e.defectCount) AS defectsCount FROM MachineEvent e WHERE e.machineId = :machineId AND e.eventTime >= :start AND e.eventTime < :end")
    MachineStatsQueryResult getMachineStats(String machineId, Instant start, Instant end);

    @Query("SELECT e.lineId, SUM(e.defectCount) AS totalDefects, COUNT(e) AS eventCount FROM MachineEvent e WHERE e.factoryId = :factoryId AND e.eventTime >= :from AND e.eventTime < :to GROUP BY e.lineId ORDER BY SUM(e.defectCount) DESC")
    List<LineStatsResponse> findTopDefectLines(String factoryId, Instant from, Instant to, Pageable limit);
    
    @Modifying
    @Query(value = """
        INSERT INTO machine_events (event_id, event_time, received_time, machine_id, duration_ms, defect_count, line_id, factory_id)
        VALUES (:#{#event.eventId}, :#{#event.eventTime}, :#{#event.receivedTime}, :#{#event.machineId}, 
                :#{#event.durationMs}, :#{#event.defectCount}, :#{#event.lineId}, :#{#event.factoryId})
        ON CONFLICT (event_id) DO UPDATE SET
            event_time = EXCLUDED.event_time,
            received_time = EXCLUDED.received_time,
            machine_id = EXCLUDED.machine_id,
            duration_ms = EXCLUDED.duration_ms,
            defect_count = EXCLUDED.defect_count,
            line_id = EXCLUDED.line_id,
            factory_id = EXCLUDED.factory_id
        WHERE EXCLUDED.received_time > machine_events.received_time
        """, nativeQuery = true)
    void upsertEvent(@Param("event") MachineEvent event);
}

