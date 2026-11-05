package com.familyhealth.medication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, UUID> {

	List<MedicationSchedule> findByMedicationIdOrderByTimeOfDayAsc(UUID medicationId);

	List<MedicationSchedule> findByMedicationIdIn(Collection<UUID> medicationIds);

	Optional<MedicationSchedule> findByIdAndMedicationId(UUID id, UUID medicationId);

}
