package com.familyhealth.emergencycard;

import java.util.UUID;
import com.familyhealth.emergencycard.EmergencyCardDtos.CardResponse;
import com.familyhealth.emergencycard.EmergencyCardDtos.LinkResponse;
import com.familyhealth.emergencycard.EmergencyCardDtos.UpdateRequest;
import com.familyhealth.identity.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every action needs MANAGE (docs/05 section 8). */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/emergency-card")
class EmergencyCardController {

	private final EmergencyCardService service;

	EmergencyCardController(EmergencyCardService service) {
		this.service = service;
	}

	@GetMapping
	CardResponse get(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.get(principal.accountId(), profileId);
	}

	@PutMapping
	CardResponse replaceItems(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId,
			@Valid @RequestBody UpdateRequest request) {
		return service.replaceItems(principal.accountId(), profileId, request);
	}

	@PostMapping("/enable")
	LinkResponse enable(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.enable(principal.accountId(), profileId);
	}

	@PostMapping("/rotate")
	LinkResponse rotate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.rotate(principal.accountId(), profileId);
	}

	@PostMapping("/disable")
	CardResponse disable(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID profileId) {
		return service.disable(principal.accountId(), profileId);
	}

}
