package com.familyhealth.emergencycard;

import com.familyhealth.emergencycard.EmergencyCardDtos.PublicCard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only route that needs no sign-in to read health information, and only the approved lines of
 * an enabled card. Rate limited, never cached, and marked for search engines to skip (docs/06 section 6).
 */
@RestController
@RequestMapping("/api/v1/public/emergency-cards")
class PublicEmergencyCardController {

	private final EmergencyCardService service;

	private final PublicCardRateLimiter rateLimiter;

	PublicEmergencyCardController(EmergencyCardService service, PublicCardRateLimiter rateLimiter) {
		this.service = service;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/{token}")
	ResponseEntity<PublicCard> read(@PathVariable String token, HttpServletRequest request) {
		rateLimiter.acquire(request.getRemoteAddr());
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.header("X-Robots-Tag", "noindex, nofollow, noarchive")
			.body(service.read(token));
	}

}
