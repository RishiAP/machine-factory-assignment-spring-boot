package com.example.machine_factory.dto;

public class MachineStatsQueryResult {
    private long eventsCount;
    private Long defectsCount;

    public MachineStatsQueryResult(long eventsCount, Long defectsCount) {
        this.eventsCount = eventsCount;
        this.defectsCount = defectsCount;
    }

    public long getEventsCount() {
        return eventsCount;
    }

    public void setEventsCount(long eventsCount) {
        this.eventsCount = eventsCount;
    }

    public Long getDefectsCount() {
        return defectsCount;
    }

    public void setDefectsCount(Long defectsCount) {
        this.defectsCount = defectsCount;
    }
}