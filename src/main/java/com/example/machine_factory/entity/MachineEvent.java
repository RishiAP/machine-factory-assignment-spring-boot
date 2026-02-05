package com.example.machine_factory.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "machine_events")
public class MachineEvent {
    @Id
    private String eventId;
    private Instant eventTime;
    private Instant receivedTime;
    private String machineId;
    private int durationMs;
    private Integer defectCount;
    private String lineId;
    private String factoryId;

    public MachineEvent() {
    }

    public MachineEvent(String eventId, Instant eventTime, Instant receivedTime, String machineId, int durationMs,
            Integer defectCount, String lineId, String factoryId) {
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.receivedTime = receivedTime;
        this.machineId = machineId;
        this.durationMs = durationMs;
        this.defectCount = defectCount;
        this.lineId = lineId;
        this.factoryId = factoryId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Instant getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(Instant receivedTime) {
        this.receivedTime = receivedTime;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(int durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getDefectCount() {
        return defectCount;
    }

    public void setDefectCount(Integer defectCount) {
        this.defectCount = defectCount;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(String factoryId) {
        this.factoryId = factoryId;
    }

    @Override
    public String toString() {
        return "MachineEvent [eventId=" + eventId + ", eventTime=" + eventTime + ", receivedTime=" + receivedTime
                + ", machineId=" + machineId + ", durationMs=" + durationMs + ", defectCount=" + defectCount + ", lineId=" + lineId + ", factoryId=" + factoryId + "]";
    }

    @Override
    public boolean equals(Object o) {
        if(o == null || o.getClass() != this.getClass()) {
            return false;
        }
        MachineEvent other = (MachineEvent) o;
        return this.eventId.equals(other.eventId) && this.eventTime.equals(other.eventTime)
                && this.machineId.equals(other.machineId) && this.durationMs == other.durationMs
                && (this.defectCount == null ? other.defectCount == null : this.defectCount.equals(other.defectCount))
                && (this.lineId == null ? other.lineId == null : this.lineId.equals(other.lineId))
                && (this.factoryId == null ? other.factoryId == null : this.factoryId.equals(other.factoryId));
    }
}
