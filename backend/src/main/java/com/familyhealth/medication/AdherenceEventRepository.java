package com.familyhealth.medication;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdherenceEventRepository extends JpaRepository<AdherenceEvent, UUID> {

	boolean existsByScheduleIdAndScheduledFor(UUID scheduleId, Instant scheduledFor);

	List<AdherenceEvent> findByScheduleIdInAndScheduledForIn(Collection<UUID> scheduleIds, Collection<Instant> doses);

}
