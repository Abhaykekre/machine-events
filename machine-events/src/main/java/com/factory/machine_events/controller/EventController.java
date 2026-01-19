package com.factory.machine_events.controller;

import com.factory.machine_events.dto.BatchResponse;
import com.factory.machine_events.dto.EventRequest;
import com.factory.machine_events.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchResponse> ingestBatch(@RequestBody List<EventRequest> events) {
        // Set receivedTime on server (as per assignment requirement)
        Instant now = Instant.now();
        events.forEach(event -> {
            if (event.getReceivedTime() == null) {
                event.setReceivedTime(now);
            }
        });

        BatchResponse response = eventService.processBatch(events);
        return ResponseEntity.ok(response);
    }
}