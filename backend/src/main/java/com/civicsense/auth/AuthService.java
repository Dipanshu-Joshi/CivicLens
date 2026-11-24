package com.civicsense.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import com.civicsense.security.UpgradingPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.admin.AdminExceptions;
import com.civicsense.audit.Audited;
import com.civicsense.auth.dto.AuthResponses.DepartmentContextResponse;
import com.civicsense.auth.dto.AuthResponses.DepartmentRegistrationResponse;
import com.civicsense.auth.dto.AuthResponses.DepartmentSummary;
import com.civicsense.auth.dto.AuthResponses.MeResponse;
import com.civicsense.auth.dto.AuthResponses.TokenResponse;
import com.civicsense.auth.dto.AuthResponses.UserInfo;
import com.civicsense.auth.dto.AuthResponses.VerifyDepartmentRequest;
import com.civicsense.auth.dto.ForgotPasswordRequest;
import com.civicsense.auth.dto.LoginRequest;
import com.civicsense.auth.dto.RegisterCitizenRequest;
import com.civicsense.auth.dto.RegisterDepartmentRequest;
import com.civicsense.auth.dto.ResetPasswordRequest;
import com.civicsense.auth.exception.AuthExceptions.AccountLockedException;
import com.civicsense.auth.exception.AuthExceptions.DepartmentAlreadyExistsException;
import com.civicsense.auth.exception.AuthExceptions.DepartmentNotFoundException;
import com.civicsense.auth.exception.AuthExceptions.EmailAlreadyExistsException;
import com.civicsense.auth.exception.AuthExceptions.InvalidCredentialsException;
import com.civicsense.auth.exception.AuthExceptions.InvalidResetTokenException;
import com.civicsense.auth.exception.AuthExceptions.InvalidTokenException;
import com.civicsense.auth.exception.AuthExceptions.InvalidVerificationDecisionException;
import com.civicsense.auth.exception.AuthExceptions.RateLimitExceededException;
import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.Role;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.domain.PasswordResetToken;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.RefreshToken;
import com.civicsense.identity.RefreshTokenRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.notification.NotificationService;
import com.civicsense.repository.OutboxEventRepository;
import com.civicsense.repository.PasswordResetTokenRepository;
import com.civicsense.security.AuthenticatedUser;
import com.civicsense.security.JwtService;
import com.civicsense.security.PrincipalFactory;
import com.civicsense.security.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** All authentication and authorization logic lives here, in Spring Boot. */
@Service
public class AuthService {

    public record IssuedTokens(TokenResponse tokens, String rawRefreshToken) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PASSWORD_RESET_MESSAGE =
            "If the account exists, a password reset link has been sent.";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentAdminRepository departmentAdminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UpgradingPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalFactory principalFactory;
    private final RateLimitService rateLimitService;
    private final RefreshTokenRevocationService revocationService;
    private final NotificationService notifications;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final long refreshTokenDays;
    private final int maxFailedAttempts;
    private final long lockoutMinutes;
    private final int resetTokenMinutes;
    private final int resetRateLimit;
    private final long resetRateWindowMinutes;
    private final String resetUrl;

    public AuthService(
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            DepartmentAdminRepository departmentAdminRepository,
            RefreshTokenRepository refreshTokenRepository,
            UpgradingPasswordEncoder passwordEncoder,
            JwtService jwtService,
            PrincipalFactory principalFactory,
            RateLimitService rateLimitService,
            RefreshTokenRevocationService revocationService,
            NotificationService notifications,
            PasswordResetTokenRepository passwordResetTokens,
            OutboxEventRepository outboxEvents,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${civicsense.auth.refresh-token-days}") long refreshTokenDays,
            @Value("${civicsense.auth.max-failed-attempts}") int maxFailedAttempts,
            @Value("${civicsense.auth.lockout-minutes}") long lockoutMinutes,
            @Value("${civicsense.auth.reset-token-minutes:30}") int resetTokenMinutes,
            @Value("${civicsense.auth.reset-rate-limit:5}") int resetRateLimit,
            @Value("${civicsense.auth.reset-rate-window-minutes:15}") long resetRateWindowMinutes,
            @Value("${civicsense.auth.reset-url:http://localhost:5173/reset-password}") String resetUrl) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.departmentAdminRepository = departmentAdminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.principalFactory = principalFactory;
        this.rateLimitService = rateLimitService;
        this.revocationService = revocationService;
        this.notifications = notifications;
        this.passwordResetTokens = passwordResetTokens;
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.refreshTokenDays = refreshTokenDays;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.resetTokenMinutes = resetTokenMinutes;
        this.resetRateLimit = resetRateLimit;
        this.resetRateWindowMinutes = resetRateWindowMinutes;
        this.resetUrl = resetUrl;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public UUID registerCitizen(RegisterCitizenRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CITIZEN);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setLocale(request.locale() == null || request.locale().isBlank() ? "en" : request.locale());
        return userRepository.save(user).getId();
    }

    @Transactional
    public DepartmentRegistrationResponse registerDepartment(RegisterDepartmentRequest request) {
        RegisterDepartmentRequest.Department dept = request.department();
        RegisterDepartmentRequest.Administrator admin = request.administrator();
        String officialEmail = normalizeEmail(dept.officialEmail());
        String adminEmail = normalizeEmail(admin.email());

        if (departmentRepository.existsByOfficialEmailIgnoreCase(officialEmail)) {
            throw new DepartmentAlreadyExistsException();
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            throw new EmailAlreadyExistsException();
        }

        Department department = new Department();
        department.setName(dept.name().trim());
        department.setDepartmentCode(generateDepartmentCode(dept.departmentType()));
        department.setDepartmentType(dept.departmentType().trim());
        department.setDescription(dept.description());
        department.setOfficialEmail(officialEmail);
        department.setContactNumber(dept.contactNumber());
        department.setOfficeAddress(dept.officeAddress());
        department.setCity(dept.city());
        department.setState(dept.state());
        department.setCountry(dept.country());
        department.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        department.setActive(true);
        department.setAcceptsComplaints(false);
        Department savedDepartment = departmentRepository.save(department);

        User owner = new User();
        owner.setEmail(adminEmail);
        owner.setPasswordHash(passwordEncoder.encode(admin.password()));
        owner.setRole(Role.DEPARTMENT_ADMIN);
        // Account is active so the owner can log in and see PENDING status;
        // /department/** stays 403 until the department is VERIFIED.
        owner.setAccountStatus(AccountStatus.ACTIVE);
        owner.setFullName(admin.fullName().trim());
        owner.setPhone(admin.phone());
        User savedOwner = userRepository.save(owner);

        DepartmentAdmin membership = new DepartmentAdmin();
        membership.setUserId(savedOwner.getId());
        membership.setDepartmentId(savedDepartment.getId());
        membership.setAdminLevel(AdminLevel.OWNER);
        membership.setPrimaryAdmin(true);
        membership.setTitle("Owner");
        departmentAdminRepository.save(membership);
        userRepository.findByRole(Role.SUPER_ADMIN).forEach(superAdminUser -> notifications.notify(
                superAdminUser.getId(), null, NotificationType.DEPARTMENT_APPROVAL_REQUIRED,
                "Department approval required",
                savedDepartment.getName() + " submitted an application for verification."));

        return new DepartmentRegistrationResponse(
                savedDepartment.getId(),
                savedDepartment.getVerificationStatus(),
                "Application submitted for verification");
    }

    // -----------------------------------------------------------------------
    // Password reset (Phase 10)
    // -----------------------------------------------------------------------

    /** Always returns the same message; unknown/inactive accounts create no token. */
    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request, String ipAddress) {
        consumeResetRateLimit("password-reset-request:", ipAddress);

        Optional<User> found = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()));
        if (found.isEmpty() || found.get().getAccountStatus() != AccountStatus.ACTIVE) {
            return PASSWORD_RESET_MESSAGE;
        }

        User user = found.get();
        Instant now = clock.instant();
        passwordResetTokens.invalidateActiveForUser(user.getId(), now);
        String rawToken = generateRawToken();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hash(rawToken));
        resetToken.setExpiresAt(now.plusSeconds(resetTokenMinutes * 60L));
        PasswordResetToken saved = passwordResetTokens.save(resetToken);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientUserId", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("tokenId", saved.getId().toString());
        payload.put("resetToken", rawToken);
        payload.put("resetUrl", resetUrl + (resetUrl.contains("?") ? "&token=" : "?token=") + rawToken);
        payload.put("expiresAt", resetToken.getExpiresAt().toString());

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventType("auth.password_reset_requested");
        outbox.setAggregateType("user");
        outbox.setAggregateId(user.getId().toString());
        outbox.setPayload(writeJson(payload));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setIdempotencyKey(UUID.randomUUID().toString());
        outbox.setCreatedAt(now);
        outboxEvents.save(outbox);

        return PASSWORD_RESET_MESSAGE;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress) {
        consumeResetRateLimit("password-reset-confirm:", ipAddress);

        PasswordResetToken token = passwordResetTokens.findByTokenHash(hash(request.token().trim()))
                .orElseThrow(InvalidResetTokenException::new);
        Instant now = clock.instant();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)
                || token.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
            throw new InvalidResetTokenException();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        token.setUsedAt(now);
        passwordResetTokens.save(token);
        revocationService.revokeAllForUser(user.getId());
    }

    private void consumeResetRateLimit(String keyPrefix, String ipAddress) {
        Duration window = Duration.ofMinutes(resetRateWindowMinutes);
        if (!rateLimitService.tryConsume(keyPrefix + safeIp(ipAddress), resetRateLimit, window)) {
            throw new RateLimitExceededException(window.toSeconds());
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize password reset outbox payload", exception);
        }
    }

    // -----------------------------------------------------------------------
    // Login / refresh / logout
    // -----------------------------------------------------------------------

    /** Not @Transactional: failed-attempt counters must survive the exception. */
    public IssuedTokens login(LoginRequest request, String ipAddress, String userAgent) {
        rateLimitService.consume("login:" + safeIp(ipAddress));

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant())) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedAttempt(user);
            throw new InvalidCredentialsException();
        }
        if (passwordEncoder.upgradeRequired(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(clock.instant());
        userRepository.save(user);

        return issueTokens(user, ipAddress, userAgent);
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException();
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidTokenException::new);
        Instant now = clock.instant();

        if (token.getRevokedAt() != null) {
            // Reuse of a rotated token: revoke the entire token family in a
            // separate transaction so the revocation survives this 401 rollback.
            revocationService.revokeAllForUser(token.getUser().getId());
            throw new InvalidTokenException();
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new InvalidTokenException();
        }
        User user = token.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTokenException();
        }

        IssuedTokens issued = issueTokens(user, ipAddress, userAgent);
        RefreshToken replacement = refreshTokenRepository.findByTokenHash(hash(issued.rawRefreshToken()))
                .orElseThrow(InvalidTokenException::new);
        token.setRevokedAt(now);
        token.setReplacedBy(replacement.getId());
        return issued;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.setRevokedAt(clock.instant());
                    }
                });
    }

    // -----------------------------------------------------------------------
    // Current user / department admin endpoints
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser principal) {
        User user = userRepository.findById(principal.id()).orElseThrow(InvalidTokenException::new);
        AuthenticatedUser fresh = principalFactory.from(user);
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAccountStatus(),
                fresh.departmentVerificationStatus(),
                fresh.departmentId(),
                permissions(fresh));
    }

    @Transactional(readOnly = true)
    public DepartmentContextResponse departmentContext(AuthenticatedUser principal) {
        Department department = departmentRepository.findById(principal.departmentId())
                .orElseThrow(DepartmentNotFoundException::new);
        return new DepartmentContextResponse(
                department.getId(),
                department.getName(),
                department.getDepartmentCode(),
                department.getVerificationStatus(),
                department.isAcceptsComplaints());
    }

    // -----------------------------------------------------------------------
    // Super-admin verification workflow
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DepartmentSummary> listDepartments() {
        return departmentRepository.findAll().stream().map(this::toSummary).toList();
    }

    @Transactional
    @Audited(action = "DEPARTMENT_VERIFY", entityType = "department")
    public DepartmentSummary verifyDepartment(
            UUID departmentId, VerifyDepartmentRequest request, AuthenticatedUser admin) {
        VerificationStatus decision = request.decision();
        if (decision != VerificationStatus.VERIFIED
                && decision != VerificationStatus.REJECTED
                && decision != VerificationStatus.SUSPENDED) {
            throw new InvalidVerificationDecisionException();
        }
        // VERIFIED remains backward compatible with callers that omit a reason.
        // Rejection and suspension are adverse decisions and always need one.
        String reason = decision == VerificationStatus.VERIFIED
                ? null : AdminExceptions.requireReason(request.reason());

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(DepartmentNotFoundException::new);
        department.setVerificationStatus(decision);
        department.setVerifiedAt(decision == VerificationStatus.VERIFIED ? clock.instant() : null);
        department.setVerifiedByUserId(admin.id());
        department.setRejectionReason(reason);
        department.setAcceptsComplaints(decision == VerificationStatus.VERIFIED);
        NotificationType type = decision == VerificationStatus.VERIFIED
                ? NotificationType.DEPARTMENT_VERIFIED : NotificationType.DEPARTMENT_REJECTED;
        departmentAdminRepository.findByDepartmentId(departmentId).forEach(departmentAdmin -> notifications.notify(
                departmentAdmin.getUserId(), null, type,
                decision == VerificationStatus.VERIFIED ? "Department verified" : "Department verification update",
                decision == VerificationStatus.VERIFIED
                        ? "Your department can now accept complaints."
                        : "Your department verification status is " + decision + ". " + (reason == null ? "" : reason)));
        return toSummary(department);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private IssuedTokens issueTokens(User user, String ipAddress, String userAgent) {
        AuthenticatedUser principal = principalFactory.from(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefresh = generateRawToken();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawRefresh));
        token.setExpiresAt(clock.instant().plusSeconds(refreshTokenDays * 24 * 60 * 60));
        token.setIpAddress(safeIp(ipAddress));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 1000)));
        refreshTokenRepository.save(token);

        TokenResponse response = new TokenResponse(
                accessToken,
                "Bearer",
                jwtService.accessTokenTtlSeconds(),
                new UserInfo(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        principal.departmentId(),
                        principal.departmentVerificationStatus()));
        return new IssuedTokens(response, rawRefresh);
    }

    /** Persisted in its own repository transaction so the counter survives the thrown 401. */
    private void recordFailedAttempt(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
            user.setLockedUntil(clock.instant().plusSeconds(lockoutMinutes * 60));
        }
        userRepository.save(user);
    }

    private List<String> permissions(AuthenticatedUser principal) {
        return switch (principal.role()) {
            case SUPER_ADMIN -> List.of("ADMIN_ALL");
            case DEPARTMENT_ADMIN -> principal.isVerifiedDepartmentAdmin()
                    ? List.of("COMPLAINT_VIEW_DEPARTMENT", "COMPLAINT_ASSIGN", "COMPLAINT_UPDATE_STATUS")
                    : List.of();
            case CITIZEN -> List.of("COMPLAINT_SUBMIT", "COMPLAINT_VIEW_OWN");
        };
    }

    private DepartmentSummary toSummary(Department department) {
        return new DepartmentSummary(
                department.getId(),
                department.getName(),
                department.getDepartmentCode(),
                department.getDepartmentType(),
                department.getOfficialEmail(),
                department.getCity(),
                department.getVerificationStatus(),
                department.isActive(),
                department.isAcceptsComplaints(),
                department.getVerifiedAt());
    }

    private String generateDepartmentCode(String departmentType) {
        String prefix = departmentType.replaceAll("[^A-Za-z]", "").toUpperCase();
        prefix = prefix.substring(0, Math.min(4, Math.max(1, prefix.length())));
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = prefix + "-" + suffix;
            if (!departmentRepository.existsByDepartmentCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique department code");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
