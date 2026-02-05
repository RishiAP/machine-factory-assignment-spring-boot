package com.example.machine_factory.dto;

public class LineStatsResponse {
    private String lineId;
    private Long totalDefects;
    private long eventCount;
    private Double defectsPercent;

    public LineStatsResponse(String lineId, Long totalDefects, long eventCount) {
        this.lineId = lineId;
        this.totalDefects = totalDefects;
        this.eventCount = eventCount;
        this.defectsPercent = eventCount == 0 || totalDefects == null ? null : Math.round((totalDefects * 100.0) / eventCount*100.0)/100.0;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public Long getTotalDefects() {
        return totalDefects;
    }

    public void setTotalDefects(Long totalDefects) {
        this.totalDefects = totalDefects;
    }

    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }

    public Double getDefectsPercent() {
        return defectsPercent;
    }

    public void setDefectsPercent(Double defectsPercent) {
        this.defectsPercent = defectsPercent;
    }
}