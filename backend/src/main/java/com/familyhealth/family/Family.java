package com.familyhealth.family;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "family")
class Family extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	protected Family() {
	}

	Family(String name, UUID createdBy) {
		this.name = name;
		this.createdBy = createdBy;
	}

	String name() {
		return name;
	}

}
