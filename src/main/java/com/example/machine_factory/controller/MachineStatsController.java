package com.example.machine_factory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.machine_factory.dto.LineStatsResponse;
import com.example.machine_factory.dto.MachineStatsResponse;
import com.example.machine_factory.service.MachineStatsService;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping(path = "/stats")
public class MachineStatsController {
    private final MachineStatsService machineStatsService;

    public MachineStatsController(MachineStatsService machineStatsService) {
        this.machineStatsService = machineStatsService;
    }

    @GetMapping
    public MachineStatsResponse getMachineStats(@RequestParam String machineId, @RequestParam Instant start, @RequestParam Instant end) {
        return machineStatsService.getMachineStats(machineId, start, end);
    }

    @GetMapping("/top-defect-lines")
    public List<LineStatsResponse> getTopDefectLines(@RequestParam String factoryId, @RequestParam Instant from, @RequestParam Instant to, @RequestParam int limit) {
        return machineStatsService.getTopDefectLines(factoryId, from, to, limit);
    }
    
}
