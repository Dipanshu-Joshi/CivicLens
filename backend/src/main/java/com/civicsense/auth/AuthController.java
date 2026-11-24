package com.civicsense.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.auth.AuthService.IssuedTokens;
import com.civicsense.auth.dto.AuthResponses.DepartmentRegistrationResponse;
import com.civicsense.auth.dto.AuthResponses.MeResponse;
import com.civicsense.auth.dto.AuthResponses.MessageResponse;
import com.civicsense.auth.dto.AuthResponses.TokenResponse;
import com.civicsense.auth.dto.ForgotPasswordRequest;
import com.civicsense.auth.dto.LoginRequest;
import com.civicsense.auth.dto.RegisterCitizenRequest;
import com.civicsense.auth.dto.RegisterDepartmentRequest;
import com.civicsense.auth.dto.ResetPasswordRequest;
import com.civicsense.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final long refreshTokenDays;

    public AuthController(
            AuthService authService,
            @Value("${civicsense.auth.refresh-cookie-name}") String cookieName,
            @Value("${civicsense.auth.refresh-cookie-secure}") boolean cookieSecure,
            @Value("${civicsense.auth.refresh-token-days}") long refreshTokenDays) {
        this.authService = authService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.refreshTokenDays = refreshTokenDays;
    }

    @PostMapping("/register/citizen")
    ResponseEntity<MessageResponse> registerCitizen(@Valid @RequestBody RegisterCitizenRequest request) {
        authService.registerCitizen(request);
        return ResponseEntity.status(201).body(new MessageResponse("Registration successful"));
    }

    @PostMapping("/register/department")
    ResponseEntity<DepartmentRegistrationResponse> registerDepartment(
            @Valid @RequestBody RegisterDepartmentRequest request) {
        return ResponseEntity.status(201).body(authService.registerDepartment(request));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        IssuedTokens issued = authService.login(
                request, httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawRefreshToken()).toString())
                .body(issued.tokens());
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${civicsense.auth.refresh-cookie-name}", required = false) String rawRefresh,
            HttpServletRequest httpRequest) {
        IssuedTokens issued = authService.refresh(
                rawRefresh, httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued.rawRefreshToken()).toString())
                .body(issued.tokens());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = "${civicsense.auth.refresh-cookie-name}", required = false) String rawRefresh) {
        authService.logout(rawRefresh);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(new MessageResponse(
                authService.requestPasswordReset(request, httpRequest.getRemoteAddr())));
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        authService.resetPassword(request, httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.me(principal);
    }

    private ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from(cookieName, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(refreshTokenDays))
                .build();
    }

    private ResponseCookie clearedCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
