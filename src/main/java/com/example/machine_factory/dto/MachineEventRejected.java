package com.example.machine_factory.dto;

public class MachineEventRejected {
    private String eventId;
    private RejectionReason reason;

    public MachineEventRejected(String eventId, RejectionReason reason) {
        this.eventId = eventId;
        this.reason = reason;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public RejectionReason getReason() {
        return reason;
    }

    public void setReason(RejectionReason reason) {
        this.reason = reason;
    }
}