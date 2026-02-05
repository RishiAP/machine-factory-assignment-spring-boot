package com.example.machine_factory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.machine_factory.dto.MachineEventsBatchResponse;
import com.example.machine_factory.entity.MachineEvent;
import com.example.machine_factory.service.MachineEventService;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping(path = "/events/batch")
public class MachineEventsBatchController {
    private final MachineEventService machineEventService;

    public MachineEventsBatchController(MachineEventService machineEventService) {
        this.machineEventService = machineEventService;
    }

    @PostMapping
    public MachineEventsBatchResponse postMethodName(@RequestBody List<MachineEvent> events) {
        return machineEventService.saveBatch(events);
    }
}
