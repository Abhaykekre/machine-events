package com.factory.machine_events.service;

import com.factory.machine_events.dto.StatsResponse;
import com.factory.machine_events.dto.TopDefectLineResponse;
import com.factory.machine_events.model.MachineEvent;
import com.factory.machine_events.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private static final double HEALTHY_THRESHOLD = 2.0;

    private final EventRepository eventRepository;

    public StatsService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public StatsResponse getStats(String machineId, Instant start, Instant end) {
        List<MachineEvent> events = eventRepository.findByMachineIdAndTimeRange(
                machineId, start, end
        );

        long eventsCount = events.size();

        // Count defects, ignoring defectCount = -1
        long defectsCount = events.stream()
                .filter(e -> e.getDefectCount() != null && e.getDefectCount() >= 0)
                .mapToLong(MachineEvent::getDefectCount)
                .sum();

        // Calculate window hours
        double windowSeconds = Duration.between(start, end).getSeconds();
        double windowHours = windowSeconds / 3600.0;

        // Calculate average defect rate
        double avgDefectRate = windowHours > 0 ? defectsCount / windowHours : 0.0;

        // Determine status
        String status = avgDefectRate < HEALTHY_THRESHOLD ? "Healthy" : "Warning";

        StatsResponse response = new StatsResponse();
        response.setMachineId(machineId);
        response.setStart(start);
        response.setEnd(end);
        response.setEventsCount(eventsCount);
        response.setDefectsCount(defectsCount);
        response.setAvgDefectRate(Math.round(avgDefectRate * 100.0) / 100.0);
        response.setStatus(status);

        return response;
    }

    public List<TopDefectLineResponse> getTopDefectLines(
            String factoryId, Instant from, Instant to, int limit) {

        List<Object[]> results = eventRepository.findTopDefectLines(factoryId, from, to);

        return results.stream()
                .limit(limit)
                .map(row -> new TopDefectLineResponse(
                        (String) row[0],      // lineId
                        ((Number) row[1]).longValue(),  // totalDefects
                        ((Number) row[2]).longValue()   // eventCount
                ))
                .collect(Collectors.toList());
    }
}