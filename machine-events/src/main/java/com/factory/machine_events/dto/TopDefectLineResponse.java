package com.factory.machine_events.dto;

public class TopDefectLineResponse {

    private String lineId;
    private long totalDefects;
    private long eventCount;
    private double defectsPercent;

    // Constructors
    public TopDefectLineResponse() {}

    public TopDefectLineResponse(String lineId, long totalDefects, long eventCount) {
        this.lineId = lineId;
        this.totalDefects = totalDefects;
        this.eventCount = eventCount;
        // Calculate defects per 100 events, rounded to 2 decimals
        this.defectsPercent = eventCount > 0 ?
                Math.round((totalDefects * 10000.0 / eventCount)) / 100.0 : 0.0;
    }

    // Getters and Setters
    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public long getTotalDefects() {
        return totalDefects;
    }

    public void setTotalDefects(long totalDefects) {
        this.totalDefects = totalDefects;
    }

    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long eventCount) {
        this.eventCount = eventCount;
    }

    public double getDefectsPercent() {
        return defectsPercent;
    }

    public void setDefectsPercent(double defectsPercent) {
        this.defectsPercent = defectsPercent;
    }
}