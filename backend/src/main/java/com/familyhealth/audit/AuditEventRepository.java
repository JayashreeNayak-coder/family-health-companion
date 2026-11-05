package com.familyhealth.audit;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert only: the audit log is append-only, so no update or delete method is offered. */
interface AuditEventRepository extends Repository<AuditEvent, UUID> {

	void save(AuditEvent event);

}
