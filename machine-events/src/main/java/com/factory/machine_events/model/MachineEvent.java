package com.factory.machine_events.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "machine_events", indexes = {
        @Index(name = "idx_event_id", columnList = "eventId", unique = true),
        @Index(name = "idx_machine_time", columnList = "machineId,eventTime"),
        @Index(name = "idx_line_time", columnList = "lineId,eventTime")
})
public class MachineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false)
    private Instant eventTime;

    @Column(nullable = false)
    private Instant receivedTime;

    @Column(nullable = false, length = 50)
    private String machineId;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private Integer defectCount;

    @Column(length = 50)
    private String lineId;

    @Column(length = 50)
    private String factoryId;

    @Version
    private Long version;

    // Constructors
    public MachineEvent() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // Equals and HashCode based on eventId
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineEvent that = (MachineEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    // Helper method to check if payloads are identical
    public boolean hasSamePayload(MachineEvent other) {
        return Objects.equals(this.eventTime, other.eventTime) &&
                Objects.equals(this.machineId, other.machineId) &&
                Objects.equals(this.durationMs, other.durationMs) &&
                Objects.equals(this.defectCount, other.defectCount) &&
                Objects.equals(this.lineId, other.lineId) &&
                Objects.equals(this.factoryId, other.factoryId);
    }

    @Override
    public String toString() {
        return "MachineEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", eventTime=" + eventTime +
                ", machineId='" + machineId + '\'' +
                ", durationMs=" + durationMs +
                ", defectCount=" + defectCount +
                '}';
    }
}