package com.familyhealth.identity;

import com.familyhealth.identity.AuthDtos.AccountResponse;
import com.familyhealth.identity.AuthDtos.CsrfResponse;
import com.familyhealth.identity.AuthDtos.LoginRequest;
import com.familyhealth.identity.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

	AuthController(AuthService authService, LoginSession loginSession) {
		this.authService = authService;
		this.loginSession = loginSession;
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
