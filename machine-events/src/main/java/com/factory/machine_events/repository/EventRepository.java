package com.factory.machine_events.repository;

import com.factory.machine_events.model.MachineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<MachineEvent, Long> {

    // Find by eventId for deduplication check
    Optional<MachineEvent> findByEventId(String eventId);

    // Find events in time range for stats
    @Query("SELECT e FROM MachineEvent e WHERE e.machineId = :machineId " +
            "AND e.eventTime >= :start AND e.eventTime < :end")
    List<MachineEvent> findByMachineIdAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Top defect lines query
    @Query("SELECT e.lineId, SUM(CASE WHEN e.defectCount >= 0 THEN e.defectCount ELSE 0 END), COUNT(e) " +
            "FROM MachineEvent e " +
            "WHERE e.factoryId = :factoryId " +
            "AND e.eventTime >= :from AND e.eventTime < :to " +
            "AND e.lineId IS NOT NULL " +
            "GROUP BY e.lineId " +
            "ORDER BY SUM(CASE WHEN e.defectCount >= 0 THEN e.defectCount ELSE 0 END) DESC")
    List<Object[]> findTopDefectLines(
            @Param("factoryId") String factoryId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Batch find by eventIds for bulk operations
    List<MachineEvent> findByEventIdIn(List<String> eventIds);
}