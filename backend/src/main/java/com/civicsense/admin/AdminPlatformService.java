package com.civicsense.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.admin.AdminDtos.ActivityEntry;
import com.civicsense.admin.AdminDtos.AdminDepartmentDetail;
import com.civicsense.admin.AdminDtos.AuditLogEntry;
import com.civicsense.admin.AdminDtos.CountEntry;
import com.civicsense.admin.AdminDtos.DepartmentOperationsRequest;
import com.civicsense.admin.AdminDtos.DepartmentStatusRequest;
import com.civicsense.admin.AdminDtos.GovernanceStats;
import com.civicsense.admin.AdminDtos.PlatformOverview;
import com.civicsense.admin.AdminDtos.PlatformStats;
import com.civicsense.admin.AdminDtos.UserRoleRequest;
import com.civicsense.admin.AdminDtos.UserStatusRequest;
import com.civicsense.admin.AdminDtos.UserSummary;
import com.civicsense.admin.AdminExceptions.AdminNotFoundException;
import com.civicsense.admin.AdminExceptions.AdminValidationException;
import com.civicsense.admin.AdminExceptions.InvalidAdminActionException;
import com.civicsense.audit.Audited;
import com.civicsense.auth.RefreshTokenRevocationService;
import com.civicsense.auth.dto.AuthResponses.DepartmentSummary;
import com.civicsense.complaint.dto.ComplaintDtos.PagedResponse;
import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.notification.NotificationService;
import com.civicsense.reference.ReferenceService;
import com.civicsense.reference.dto.ReferenceDtos.CategoryResponse;
import com.civicsense.reference.dto.ReferenceDtos.JurisdictionResponse;
import com.civicsense.reference.dto.ReferenceDtos.ServiceAreaResponse;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.AuditLogRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.ComplaintStatusHistoryRepository;
import com.civicsense.repository.ReassignmentRequestRepository;
import com.civicsense.repository.RoutingDecisionRepository;
import com.civicsense.repository.ServiceAreaRepository;
import com.civicsense.security.AuthenticatedUser;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Read models and governance mutations for the super-admin platform surface. */
@Service
public class AdminPlatformService {

    private static final Set<ComplaintStatus> TERMINAL = Set.of(
            ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED);

    private static final List<ComplaintStatus> UNASSIGNED_STATUSES = List.of(
            ComplaintStatus.SUBMITTED, ComplaintStatus.PENDING_ASSIGNMENT, ComplaintStatus.UNASSIGNED);

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository statusHistory;
    private final RoutingDecisionRepository routingDecisions;
    private final AiAnalysisRepository aiAnalyses;
    private final ReassignmentRequestRepository reassignmentRequests;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final DepartmentAdminRepository departmentAdmins;
    private final RefreshTokenRevocationService revocationService;
    private final AuditLogRepository auditLogs;
    private final ServiceAreaRepository serviceAreas;
    private final ReferenceService referenceService;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    private final java.time.Clock clock;

    public AdminPlatformService(
            ComplaintRepository complaints,
            ComplaintStatusHistoryRepository statusHistory,
            RoutingDecisionRepository routingDecisions,
            AiAnalysisRepository aiAnalyses,
            ReassignmentRequestRepository reassignmentRequests,
            DepartmentRepository departments,
            UserRepository users,
            DepartmentAdminRepository departmentAdmins,
            RefreshTokenRevocationService revocationService,
            AuditLogRepository auditLogs,
            ServiceAreaRepository serviceAreas,
            ReferenceService referenceService,
            NotificationService notifications,
            EntityManager entityManager,
            java.time.Clock clock) {
        this.complaints = complaints;
        this.statusHistory = statusHistory;
        this.routingDecisions = routingDecisions;
        this.aiAnalyses = aiAnalyses;
        this.reassignmentRequests = reassignmentRequests;
        this.departments = departments;
        this.users = users;
        this.departmentAdmins = departmentAdmins;
        this.revocationService = revocationService;
        this.auditLogs = auditLogs;
        this.serviceAreas = serviceAreas;
        this.referenceService = referenceService;
        this.notifications = notifications;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // -----------------------------------------------------------------------
    // Platform insight
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformOverview overview(int periodDays, int limit) {
        return buildOverview(periodDays, limit, true);
    }

    @Transactional(readOnly = true)
    public PlatformOverview analytics(int periodDays) {
        return buildOverview(periodDays, 0, false);
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> activity(int limit) {
        return recentActivity(clampLimit(limit));
    }

    private PlatformOverview buildOverview(int periodDays, int limit, boolean includeDetails) {
        int days = Math.min(365, Math.max(1, periodDays));
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(days));

        List<Complaint> all = complaints.findAll();
        List<Complaint> inPeriod = all.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(from))
                .toList();
        List<Complaint> resolved = inPeriod.stream()
                .filter(c -> c.getResolvedAt() != null)
                .toList();

        long failedAi = aiAnalyses.findAll().stream()
                .filter(a -> a.getStatus() == AiAnalysisStatus.FAILED)
                .count();

        double averageHours = resolved.isEmpty() ? 0.0 : resolved.stream()
                .mapToDouble(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMillis()
                        / 3_600_000.0)
                .average().orElse(0.0);
        List<Complaint> withDue = resolved.stream()
                .filter(c -> c.getResolutionDueAt() != null)
                .toList();
        double compliance = withDue.isEmpty() ? 0.0
                : withDue.stream().filter(c -> !c.getResolvedAt().isAfter(c.getResolutionDueAt())).count()
                        * 100.0 / withDue.size();

        PlatformStats stats = new PlatformStats(
                inPeriod.size(),
                resolved.size(),
                inPeriod.stream().filter(c -> c.getStatus() != null && !TERMINAL.contains(c.getStatus())).count(),
                inPeriod.stream().filter(c -> c.getRelatedDepartment() == null
                        && c.getStatus() != null && UNASSIGNED_STATUSES.contains(c.getStatus())).count(),
                inPeriod.stream().filter(c -> !TERMINAL.contains(c.getStatus())
                        && c.getResolutionDueAt() != null && c.getResolutionDueAt().isBefore(now)).count(),
                failedAi,
                round(averageHours),
                round(compliance));

        List<Department> allDepartments = departments.findAll();
        var decisions = routingDecisions.findAll();
        GovernanceStats governance = new GovernanceStats(
                allDepartments.stream().filter(d -> d.getVerificationStatus() == VerificationStatus.PENDING_VERIFICATION).count(),
                allDepartments.stream().filter(d -> d.getVerificationStatus() == VerificationStatus.SUSPENDED).count(),
                decisions.stream().filter(d -> "AMBIGUOUS".equals(d.getOutcome())).count(),
                decisions.stream().filter(d -> "LOW_CONFIDENCE".equals(d.getOutcome())).count(),
                failedAi,
                reassignmentRequests.findAll().size());

        Map<Group, Long> byStatus = groupCounts(inPeriod,
                c -> c.getStatus() == null ? new Group("UNKNOWN", "Unknown")
                        : new Group(c.getStatus().name(), label(c.getStatus().name())));
        Map<Group, Long> byCategory = groupCounts(inPeriod.stream()
                        .filter(c -> c.getCategory() != null).toList(),
                c -> new Group(c.getCategory().getCode(), label(c.getCategory().getCode())));
        Map<Group, Long> byDepartment = groupCounts(inPeriod.stream()
                        .filter(c -> c.getRelatedDepartment() != null).toList(),
                c -> new Group(c.getRelatedDepartment().getId().toString(), c.getRelatedDepartment().getName()));
        Map<Group, Long> byJurisdiction = groupCounts(inPeriod.stream()
                        .filter(c -> c.getJurisdiction() != null).toList(),
                c -> new Group(c.getJurisdiction().getId().toString(), c.getJurisdiction().getName()));

        int cappedLimit = clampLimit(limit);
        List<ActivityEntry> activity = includeDetails ? recentActivity(cappedLimit) : List.of();
        List<AuditLogEntry> audit = includeDetails
                ? auditLogs.findAll(PageRequest.of(0, cappedLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .getContent().stream().map(this::toAuditEntry).toList()
                : List.of();

        return new PlatformOverview(days, stats, governance,
                toCounts(byStatus), toCounts(byCategory), toCounts(byDepartment), toCounts(byJurisdiction),
                activity, audit);
    }

    private List<ActivityEntry> recentActivity(int limit) {
        return statusHistory.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(h -> new ActivityEntry(
                        h.getComplaint().getId(),
                        h.getComplaint().getComplaintNumber(),
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getActorRole() == null ? "SYSTEM" : h.getActorRole(),
                        h.getReason(),
                        h.getCreatedAt()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Department governance
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DepartmentSummary> departments(
            String verificationStatus, Boolean isActive, String departmentType, String search) {
        VerificationStatus status = verificationStatus == null || verificationStatus.isBlank()
                ? null : parse(VerificationStatus.class, verificationStatus.trim());
        String type = blankToNull(departmentType);
        String term = blankToNull(search);
        return departments.findAll().stream()
                .filter(d -> status == null || d.getVerificationStatus() == status)
                .filter(d -> isActive == null || d.isActive() == isActive)
                .filter(d -> type == null || type.equalsIgnoreCase(d.getDepartmentType()))
                .filter(d -> term == null || matches(term, d.getName(), d.getDepartmentCode(),
                        d.getOfficialEmail(), d.getCity()))
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(AdminPlatformService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDepartmentDetail departmentDetail(UUID id) {
        Department department = requireDepartment(id);
        List<Complaint> related = complaints.findAllByRelatedDepartmentIdOrderByCreatedAtDesc(id);
        return toDetail(department, related.size(), related.stream()
                .filter(c -> !TERMINAL.contains(c.getStatus())).count(),
                departmentAdmins.findAll().stream().filter(a -> a.getDepartmentId().equals(id)).count());
    }

    @Transactional
    @Audited(action = "DEPARTMENT_STATUS", entityType = "department")
    public DepartmentSummary updateDepartmentStatus(
            UUID id, DepartmentStatusRequest request, AuthenticatedUser admin) {
        AdminExceptions.requireReason(request.reason());
        VerificationStatus target = request.status();
        if (target == VerificationStatus.PENDING_VERIFICATION) {
            throw new InvalidAdminActionException("PENDING_VERIFICATION cannot be set manually");
        }
        Department department = requireDepartment(id);
        boolean verified = target == VerificationStatus.VERIFIED;
        department.setVerificationStatus(target);
        department.setVerifiedAt(verified ? clock.instant() : null);
        department.setVerifiedByUserId(admin.id());
        department.setAcceptsComplaints(verified && department.isActive());
        department.setRejectionReason(verified ? null : request.reason().trim());
        DepartmentSummary result = toSummary(departments.save(department));
        NotificationType type = verified
                ? NotificationType.DEPARTMENT_VERIFIED : NotificationType.DEPARTMENT_REJECTED;
        departmentAdmins.findByDepartmentId(id).forEach(departmentAdmin -> notifications.notify(
                departmentAdmin.getUserId(), null, type,
                verified ? "Department verified" : "Department verification update",
                verified ? "Your department can now accept complaints."
                        : "Your department status is " + target + ". " + request.reason().trim()));
        return result;
    }

    @Transactional
    @Audited(action = "DEPARTMENT_OPERATIONS", entityType = "department")
    public AdminDepartmentDetail updateDepartmentOperations(
            UUID id, DepartmentOperationsRequest request, AuthenticatedUser admin) {
        AdminExceptions.requireReason(request.reason());
        if (request.autoAssignEnabled() == null) {
            throw new AdminValidationException("autoAssignEnabled is required");
        }
        if (request.routingPriority() != null
                && (request.routingPriority() < 0 || request.routingPriority() > 10000)) {
            throw new AdminValidationException("routingPriority must be between 0 and 10000");
        }
        if (request.maxOpenComplaints() != null
                && (request.maxOpenComplaints() < 1 || request.maxOpenComplaints() > 10000)) {
            throw new AdminValidationException("maxOpenComplaints must be between 1 and 10000 or null");
        }
        Department department = requireDepartment(id);
        if (request.routingPriority() != null) {
            department.setRoutingPriority(request.routingPriority());
        }
        department.setMaxOpenComplaints(request.maxOpenComplaints());
        department.setAutoAssignEnabled(request.autoAssignEnabled());
        departments.save(department);
        return departmentDetail(id);
    }

    // -----------------------------------------------------------------------
    // User management
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<UserSummary> users(
            String role, String status, UUID departmentId, String search, int page, int size) {
        Role roleFilter = role == null || role.isBlank() ? null : parse(Role.class, role.trim());
        AccountStatus statusFilter = status == null || status.isBlank()
                ? null : parse(AccountStatus.class, status.trim());
        String term = blankToNull(search);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        Map<UUID, UUID> userDepartments = departmentAdmins.findAll().stream()
                .collect(Collectors.toMap(a -> a.getUserId(), a -> a.getDepartmentId(), (a, b) -> a));
        List<UserSummary> matches = users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(u -> roleFilter == null || u.getRole() == roleFilter)
                .filter(u -> statusFilter == null || u.getAccountStatus() == statusFilter)
                .filter(u -> departmentId == null || departmentId.equals(userDepartments.get(u.getId())))
                .filter(u -> term == null || matches(term, u.getEmail(), u.getFullName()))
                .map(u -> toUserSummary(u, userDepartments))
                .toList();
        int from = Math.min(safePage * safeSize, matches.size());
        int to = Math.min(from + safeSize, matches.size());
        return new PagedResponse<>(matches.subList(from, to), safePage, safeSize,
                matches.size(), (int) Math.ceil((double) matches.size() / safeSize));
    }

    @Transactional
    @Audited(action = "USER_STATUS", entityType = "user")
    public UserSummary updateUserStatus(UUID id, UserStatusRequest request, AuthenticatedUser admin) {
        AdminExceptions.requireReason(request.reason());
        requireDifferentUser(id, admin);
        User target = requireUser(id);
        if (target.getRole() == Role.SUPER_ADMIN
                && target.getAccountStatus() == AccountStatus.ACTIVE
                && request.status() != AccountStatus.ACTIVE) {
            requireAnotherActiveSuperAdmin();
        }
        target.setAccountStatus(request.status());
        users.save(target);
        if (request.status() == AccountStatus.SUSPENDED || request.status() == AccountStatus.DISABLED) {
            revocationService.revokeAllForUser(id);
        }
        return toUserSummary(target, userDepartments());
    }

    @Transactional
    @Audited(action = "USER_ROLE", entityType = "user")
    public UserSummary updateUserRole(UUID id, UserRoleRequest request, AuthenticatedUser admin) {
        AdminExceptions.requireReason(request.reason());
        requireDifferentUser(id, admin);
        User target = requireUser(id);
        if (target.getRole() == Role.SUPER_ADMIN
                && target.getAccountStatus() == AccountStatus.ACTIVE
                && request.role() != Role.SUPER_ADMIN) {
            requireAnotherActiveSuperAdmin();
        }
        target.setRole(request.role());
        users.save(target);
        return toUserSummary(target, userDepartments());
    }

    // -----------------------------------------------------------------------
    // Audit exploration
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogEntry> auditLogs(
            String action, String entityType, String entityId, UUID actorId,
            String from, String to, int page, int size) {
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<com.civicsense.domain.AuditLog> query = cb.createQuery(com.civicsense.domain.AuditLog.class);
        Root<com.civicsense.domain.AuditLog> root = query.from(com.civicsense.domain.AuditLog.class);
        Predicate[] predicates = auditPredicates(cb, root, action, entityType, entityId,
                actorId, fromInstant, toInstant);
        query.where(predicates).orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        List<AuditLogEntry> content = entityManager.createQuery(query)
                .setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize)
                .getResultList().stream().map(this::toAuditEntry).toList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<com.civicsense.domain.AuditLog> countRoot = countQuery.from(com.civicsense.domain.AuditLog.class);
        countQuery.select(cb.count(countRoot))
                .where(auditPredicates(cb, countRoot, action, entityType, entityId,
                        actorId, fromInstant, toInstant));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PagedResponse<>(content, safePage, safeSize, total,
                (int) Math.ceil((double) total / safeSize));
    }

    private static Predicate[] auditPredicates(
            CriteriaBuilder cb, Root<com.civicsense.domain.AuditLog> root,
            String action, String entityType, String entityId, UUID actorId,
            Instant from, Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        if (hasText(action)) {
            predicates.add(cb.equal(root.get("action"), action.trim()));
        }
        if (hasText(entityType)) {
            predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
        }
        if (hasText(entityId)) {
            predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
        }
        if (actorId != null) {
            predicates.add(cb.equal(root.get("actor").get("id"), actorId));
        }
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private AuditLogEntry toAuditEntry(com.civicsense.domain.AuditLog log) {
        User actor = log.getActor();
        return new AuditLogEntry(
                log.getId(),
                actor == null ? null : actor.getFullName(),
                log.getActorRole(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getReason(),
                log.getCreatedAt(),
                log.getCorrelationId());
    }

    // -----------------------------------------------------------------------
    // Reference / service-area oversight
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories(boolean includeInactive) {
        return referenceService.categoryTree(includeInactive);
    }

    @Transactional(readOnly = true)
    public List<JurisdictionResponse> jurisdictions(boolean includeInactive) {
        return referenceService.jurisdictions(includeInactive);
    }

    @Transactional(readOnly = true)
    public List<ServiceAreaResponse> serviceAreas(UUID departmentId, Boolean active) {
        return serviceAreas.findAll().stream()
                .filter(a -> departmentId == null
                        || (a.getDepartment() != null && departmentId.equals(a.getDepartment().getId())))
                .filter(a -> active == null || a.isActive() == active)
                .sorted(Comparator.comparing(a -> a.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> new ServiceAreaResponse(
                        a.getId(),
                        a.getDepartment() == null ? null : a.getDepartment().getId(),
                        a.getName(),
                        a.getCoversWard(),
                        a.getCoversZone(),
                        a.getJurisdiction() == null ? null : a.getJurisdiction().getCode(),
                        a.isActive(),
                        a.getCreatedAt()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private Department requireDepartment(UUID id) {
        return departments.findById(id)
                .orElseThrow(() -> new AdminNotFoundException("Department not found"));
    }

    private User requireUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new AdminNotFoundException("User not found"));
    }

    private static void requireDifferentUser(UUID id, AuthenticatedUser admin) {
        if (admin != null && admin.id().equals(id)) {
            throw new InvalidAdminActionException("Administrators cannot change their own account");
        }
    }

    private void requireAnotherActiveSuperAdmin() {
        long activeSuperAdmins = users.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPER_ADMIN && u.getAccountStatus() == AccountStatus.ACTIVE)
                .count();
        if (activeSuperAdmins <= 1) {
            throw new InvalidAdminActionException("Cannot remove or demote the last active super admin");
        }
    }

    private Map<UUID, UUID> userDepartments() {
        return departmentAdmins.findAll().stream()
                .collect(Collectors.toMap(a -> a.getUserId(), a -> a.getDepartmentId(), (a, b) -> a));
    }

    private UserSummary toUserSummary(User user, Map<UUID, UUID> userDepartments) {
        UUID departmentId = userDepartments.get(user.getId());
        String departmentName = departmentId == null ? null
                : departments.findById(departmentId).map(Department::getName).orElse(null);
        return new UserSummary(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getAccountStatus(), departmentId, departmentName,
                user.getLastLoginAt());
    }

    private static DepartmentSummary toSummary(Department department) {
        return new DepartmentSummary(
                department.getId(), department.getName(), department.getDepartmentCode(),
                department.getDepartmentType(), department.getOfficialEmail(), department.getCity(),
                department.getVerificationStatus(), department.isActive(),
                department.isAcceptsComplaints(), department.getVerifiedAt());
    }

    private static AdminDepartmentDetail toDetail(
            Department d, long totalComplaints, long openComplaints, long adminCount) {
        return new AdminDepartmentDetail(
                d.getId(), d.getName(), d.getDepartmentCode(), d.getDepartmentType(), d.getDescription(),
                d.getOfficialEmail(), d.getContactNumber(), d.getOfficeAddress(), d.getCity(), d.getState(),
                d.getCountry(), d.getVerificationStatus(), d.isActive(), d.isAcceptsComplaints(),
                d.isAutoAssignEnabled(), d.getRoutingPriority(), d.getMaxOpenComplaints(),
                d.getVerifiedAt(), d.getVerifiedByUserId(), d.getRejectionReason(),
                d.getCreatedAt(), openComplaints, totalComplaints, adminCount);
    }

    private static <T> Map<Group, Long> groupCounts(List<T> values, Function<T, Group> keyFn) {
        return values.stream().collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.counting()));
    }

    private static List<CountEntry> toCounts(Map<Group, Long> grouped) {
        return grouped.entrySet().stream()
                .map(e -> new CountEntry(e.getKey().key(), e.getKey().label(), e.getValue()))
                .sorted(Comparator.comparingLong(CountEntry::count).reversed().thenComparing(CountEntry::key))
                .toList();
    }

    private record Group(String key, String label) {
    }

    private static String label(String value) {
        if ("ROAD".equals(value)) {
            return "Roads";
        }
        return Arrays.stream(value.split("_"))
                .map(part -> part.isEmpty() ? part
                        : part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static boolean matches(String term, String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static int clampLimit(int limit) {
        return Math.min(100, Math.max(1, limit));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AdminValidationException("Unknown value: " + value);
        }
    }

    private static Instant parseInstant(String value, String field) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toInstant();
            } catch (DateTimeParseException ex) {
                throw new AdminValidationException("Invalid " + field + " timestamp");
            }
        }
    }
}
