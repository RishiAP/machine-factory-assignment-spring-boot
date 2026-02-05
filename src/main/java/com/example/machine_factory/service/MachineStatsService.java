package com.example.machine_factory.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.machine_factory.dto.LineStatsResponse;
import com.example.machine_factory.dto.MachineStatsQueryResult;
import com.example.machine_factory.dto.MachineStatsResponse;
import com.example.machine_factory.repository.MachineEventRepository;

@Service
public class MachineStatsService {
    private final MachineEventRepository machineEventRepository;

    public MachineStatsService(MachineEventRepository machineEventRepository) {
        this.machineEventRepository = machineEventRepository;
    }

    public MachineStatsResponse getMachineStats(String machineId, Instant start, Instant end) {
        MachineStatsQueryResult result = machineEventRepository.getMachineStats(machineId, start, end);
        return new MachineStatsResponse(machineId, start, end, result.getEventsCount(), result.getDefectsCount(),
                result.getDefectsCount() == null ? null
                        : (result.getDefectsCount() / (Duration.between(start, end).getSeconds()/3600.0)));
    }

    public List<LineStatsResponse> getTopDefectLines(String factoryId, Instant from, Instant to, int limit) {
        return machineEventRepository.findTopDefectLines(factoryId, from, to, Pageable.ofSize(limit));
    }
}
