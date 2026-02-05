package com.example.machine_factory.dto;

import java.time.Instant;

public class MachineStatsResponse extends MachineStatsQueryResult {
    private String machineId;
    private Instant start;
    private Instant end;
    private Double avgDefectRate;
    private String status;
    
    public MachineStatsResponse(String machineId, Instant start, Instant end, long eventsCount, Long defectsCount, Double avgDefectRate) {
        super(eventsCount, defectsCount);
        this.machineId = machineId;
        this.start = start;
        this.end = end;
        this.avgDefectRate = avgDefectRate;
        this.status = avgDefectRate==null ? null : avgDefectRate < 2.0 ? "Healthy" : "Warning";
    }
    
    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setEnd(Instant end) {
        this.end = end;
    }

    public Double getAvgDefectRate() {
        return avgDefectRate;   
    }
    
    public void setAvgDefectRate(Double avgDefectRate) {
        this.avgDefectRate = avgDefectRate;
        this.status = avgDefectRate == null ? null : avgDefectRate < 2.0 ? "Healthy" : "Warning";
    }
    
    public String getStatus() {
        return status;
    }
}