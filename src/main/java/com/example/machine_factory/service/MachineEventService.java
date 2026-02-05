package com.example.machine_factory.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.machine_factory.dto.MachineEventRejected;
import com.example.machine_factory.dto.MachineEventsBatchResponse;
import com.example.machine_factory.dto.RejectionReason;
import com.example.machine_factory.entity.MachineEvent;
import com.example.machine_factory.repository.MachineEventRepository;

import jakarta.transaction.Transactional;

@Service
public class MachineEventService {
    private final MachineEventRepository machineEventRepository;

    public MachineEventService(MachineEventRepository machineEventRepository) {
        this.machineEventRepository = machineEventRepository;
    }

    public MachineEventsBatchResponse saveBatch(List<MachineEvent> events) {
        final Instant now = Instant.now();
        List<MachineEvent> validMachineEvents = new ArrayList<>();
        List<MachineEventRejected> rejectedMachineEvents = new ArrayList<>();
        
        // Validate - fast, no DB access
        for (MachineEvent event : events) {
            if (event.getDurationMs() < 0 || event.getDurationMs() > 21600000) {
                rejectedMachineEvents.add(
                    new MachineEventRejected(event.getEventId(), RejectionReason.INVALID_DURATION)
                );
            } else if (event.getEventTime().isAfter(now.plusSeconds(15 * 60))) {
                rejectedMachineEvents.add(
                    new MachineEventRejected(event.getEventId(), RejectionReason.FUTURE_EVENT_TIME)
                );
            } else {
                if (event.getDefectCount() != null && event.getDefectCount() == -1) {
                    event.setDefectCount(null);
                }
                validMachineEvents.add(event);
            }
        }
        
        return persistValidEvents(validMachineEvents, rejectedMachineEvents, now);
    }

    @Transactional
    protected MachineEventsBatchResponse persistValidEvents(List<MachineEvent> validMachineEvents, 
            List<MachineEventRejected> rejectedMachineEvents, Instant now) {
        
        // Dedup in-memory first (faster than stream)
        Map<String, MachineEvent> validEventsMap = new HashMap<>();
        for (MachineEvent event : validMachineEvents) {
            validEventsMap.putIfAbsent(event.getEventId(), event);
        }
        
        List<String> allValidIds = new ArrayList<>(validEventsMap.keySet());
        List<MachineEvent> existingEvents = machineEventRepository.findAllById(allValidIds);
        
        int dedupedCount = 0, updatedCount = 0;
        for (MachineEvent existingEvent : existingEvents) {
            MachineEvent newEvent = validEventsMap.get(existingEvent.getEventId());
            if (existingEvent.equals(newEvent)) {
                dedupedCount++;
                validEventsMap.remove(existingEvent.getEventId());
            } else if (newEvent.getReceivedTime() != null && 
                       newEvent.getReceivedTime().isBefore(existingEvent.getReceivedTime())) {
                validEventsMap.remove(existingEvent.getEventId());
            } else {
                updatedCount++;
            }
        }
        
        // Set received time and save
        validEventsMap.values().forEach(event -> event.setReceivedTime(now));
        machineEventRepository.saveAll(validEventsMap.values());
        
        int acceptedCount = validEventsMap.size() - updatedCount;
        
        return new MachineEventsBatchResponse(
            acceptedCount, 
            dedupedCount, 
            updatedCount, 
            rejectedMachineEvents.size(),
            rejectedMachineEvents
        );
    }
}
