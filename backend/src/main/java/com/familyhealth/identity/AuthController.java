package com.familyhealth.identity;

import com.familyhealth.identity.AuthDtos.AccountResponse;
import com.familyhealth.identity.AuthDtos.CsrfResponse;
import com.familyhealth.identity.AuthDtos.LoginRequest;
import com.familyhealth.identity.AuthDtos.PasswordResetConfirmation;
import com.familyhealth.identity.AuthDtos.PasswordResetRequest;
import com.familyhealth.identity.AuthDtos.ProvidersResponse;
import com.familyhealth.identity.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService authService;

	private final LoginSession loginSession;

	private final PasswordResetService passwordReset;

	private final boolean googleEnabled;

	AuthController(AuthService authService, LoginSession loginSession, PasswordResetService passwordReset,
			ObjectProvider<ClientRegistrationRepository> googleRegistrations) {
		this.authService = authService;
		this.loginSession = loginSession;
		this.passwordReset = passwordReset;
		this.googleEnabled = googleRegistrations.getIfAvailable() != null;
		// Says at startup why the Google button is or isn't shown. No key values are logged.
		LoggerFactory.getLogger(AuthController.class)
			.info(this.googleEnabled ? "Google sign-in is on"
					: "Google sign-in is off: GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are not both set in this process's environment");
	}

	@GetMapping("/providers")
	ProvidersResponse providers() {
		return new ProvidersResponse(googleEnabled);
	}

	/** Always 202 with no body, whether or not the email has an account. */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		passwordReset.request(request.email());
	}

	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation request) {
		passwordReset.confirm(request.token(), request.newPassword());
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	AccountResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		AccountResponse account = authService.login(body, request.getRemoteAddr());
		loginSession.start(new AccountPrincipal(account.id()), request, response);
		return account;
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@AuthenticationPrincipal AccountPrincipal principal, Authentication authentication,
			HttpServletRequest request, HttpServletResponse response) {
		authService.recordLogout(principal);
		loginSession.end(authentication, request, response);
	}

	@GetMapping("/me")
	AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
		return authService.currentAccount(principal);
	}

	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

}
