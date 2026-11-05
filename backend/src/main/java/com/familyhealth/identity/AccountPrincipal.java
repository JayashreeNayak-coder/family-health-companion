package com.familyhealth.identity;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The signed-in account, stored in the session. It holds only the ID, so no email or name is
 * written to the session tables, and Spring Session indexes sessions by this ID.
 */
public record AccountPrincipal(UUID accountId) implements AuthenticatedPrincipal, Serializable {

	@Override
	public String getName() {
		return accountId.toString();
	}

}
