package com.factory.machine_events.dto;


public class RejectionDetail {

    private String eventId;
    private String reason;

    // Constructors
    public RejectionDetail() {}

    public RejectionDetail(String eventId, String reason) {
        this.eventId = eventId;
        this.reason = reason;
    }

    // Getters and Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
