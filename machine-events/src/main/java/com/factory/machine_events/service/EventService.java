package com.factory.machine_events.service;

import com.factory.machine_events.dto.BatchResponse;
import com.factory.machine_events.dto.EventRequest;
import com.factory.machine_events.dto.RejectionDetail;
import com.factory.machine_events.model.MachineEvent;
import com.factory.machine_events.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final long MAX_DURATION_MS = 6L * 60 * 60 * 1000; // 6 hours
    private static final long FUTURE_TIME_THRESHOLD_MINUTES = 15;

    private final EventRepository eventRepository;

    // In-memory lock for each eventId to prevent race conditions
    private final ConcurrentHashMap<String, Object> eventLocks = new ConcurrentHashMap<>();

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public BatchResponse processBatch(List<EventRequest> requests) {
        BatchResponse response = new BatchResponse();
        List<RejectionDetail> rejections = new ArrayList<>();

        int accepted = 0, deduped = 0, updated = 0, rejected = 0;

        // Group by eventId for efficient processing
        Map<String, EventRequest> eventMap = new HashMap<>();
        for (EventRequest req : requests) {
            eventMap.put(req.getEventId(), req);
        }

        // Fetch existing events in bulk
        List<String> eventIds = new ArrayList<>(eventMap.keySet());
        Map<String, MachineEvent> existingEvents = eventRepository
                .findByEventIdIn(eventIds)
                .stream()
                .collect(Collectors.toMap(MachineEvent::getEventId, e -> e));

        List<MachineEvent> toSave = new ArrayList<>();

        for (EventRequest request : requests) {
            // Validation
            String validationError = validateEvent(request);
            if (validationError != null) {
                rejections.add(new RejectionDetail(request.getEventId(), validationError));
                rejected++;
                continue;
            }

            // Get lock for this eventId
            Object lock = eventLocks.computeIfAbsent(request.getEventId(), k -> new Object());

            synchronized (lock) {
                MachineEvent existing = existingEvents.get(request.getEventId());

                if (existing == null) {
                    // New event
                    MachineEvent newEvent = convertToEntity(request);
                    toSave.add(newEvent);
                    existingEvents.put(request.getEventId(), newEvent);
                    accepted++;
                } else {
                    // Check for duplicate or update
                    MachineEvent incoming = convertToEntity(request);

                    if (existing.hasSamePayload(incoming)) {
                        // Exact duplicate - ignore
                        deduped++;
                    } else {
                        // Different payload - check receivedTime
                        if (incoming.getReceivedTime().isAfter(existing.getReceivedTime())) {
                            // Update the existing event
                            updateEvent(existing, incoming);
                            toSave.add(existing);
                            updated++;
                        } else {
                            // Older receivedTime - ignore
                            deduped++;
                        }
                    }
                }
            }
        }

        // Batch save
        if (!toSave.isEmpty()) {
            eventRepository.saveAll(toSave);
        }

        response.setAccepted(accepted);
        response.setDeduped(deduped);
        response.setUpdated(updated);
        response.setRejected(rejected);
        response.setRejections(rejections);

        return response;
    }

    private String validateEvent(EventRequest request) {
        // Check duration
        if (request.getDurationMs() == null || request.getDurationMs() < 0) {
            return "INVALID_DURATION";
        }
        if (request.getDurationMs() > MAX_DURATION_MS) {
            return "DURATION_TOO_LONG";
        }

        // Check future eventTime
        Instant now = Instant.now();
        Instant maxFutureTime = now.plus(Duration.ofMinutes(FUTURE_TIME_THRESHOLD_MINUTES));
        if (request.getEventTime().isAfter(maxFutureTime)) {
            return "FUTURE_EVENT_TIME";
        }

        // Check required fields
        if (request.getEventId() == null || request.getEventId().isEmpty()) {
            return "MISSING_EVENT_ID";
        }
        if (request.getMachineId() == null || request.getMachineId().isEmpty()) {
            return "MISSING_MACHINE_ID";
        }
        if (request.getEventTime() == null) {
            return "MISSING_EVENT_TIME";
        }

        return null; // Valid
    }

    private MachineEvent convertToEntity(EventRequest request) {
        MachineEvent event = new MachineEvent();
        event.setEventId(request.getEventId());
        event.setEventTime(request.getEventTime());

        // receivedTime is set by server
        event.setReceivedTime(request.getReceivedTime() != null ?
                request.getReceivedTime() : Instant.now());

        event.setMachineId(request.getMachineId());
        event.setDurationMs(request.getDurationMs());
        event.setDefectCount(request.getDefectCount() != null ?
                request.getDefectCount() : 0);
        event.setLineId(request.getLineId());
        event.setFactoryId(request.getFactoryId());

        return event;
    }

    private void updateEvent(MachineEvent existing, MachineEvent incoming) {
        existing.setEventTime(incoming.getEventTime());
        existing.setReceivedTime(incoming.getReceivedTime());
        existing.setMachineId(incoming.getMachineId());
        existing.setDurationMs(incoming.getDurationMs());
        existing.setDefectCount(incoming.getDefectCount());
        existing.setLineId(incoming.getLineId());
        existing.setFactoryId(incoming.getFactoryId());
    }
}