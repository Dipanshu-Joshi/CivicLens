package com.civicsense.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.complaint.StatusTransitionPolicy;
import com.civicsense.complaint.StatusTransitionPolicy.Actor;
import com.civicsense.domain.AiAnalysis;
import com.civicsense.domain.AiAnalysisSource;
import com.civicsense.domain.AiAnalysisStatus;
import com.civicsense.domain.AssignmentType;
import com.civicsense.domain.Complaint;
import com.civicsense.domain.ComplaintAssignment;
import com.civicsense.domain.ComplaintStatus;
import com.civicsense.domain.ComplaintStatusHistory;
import com.civicsense.domain.DepartmentIssueCategory;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.Jurisdiction;
import com.civicsense.domain.LocationSource;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.RoutingDecision;
import com.civicsense.domain.RoutingOutcome;
import com.civicsense.domain.Severity;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdminRepository;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.notification.NotificationService;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.ComplaintAssignmentRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.ComplaintStatusHistoryRepository;
import com.civicsense.repository.DepartmentIssueCategoryRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.repository.JurisdictionRepository;
import com.civicsense.repository.RoutingDecisionRepository;
import com.civicsense.repository.ServiceAreaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Deterministic ruleset {@code routing-v1}. AI output is advisory evidence only:
 * the selected department is derived exclusively from validated category data,
 * citizen coordinates, verified department registrations and service areas.
 */
@Service
public class RoutingEngine {

    public static final String RULESET_VERSION = "routing-v1";

    private static final int GEO_POLYGON = 1000;
    private static final int GEO_WARD = 600;
    private static final int GEO_ZONE = 400;
    private static final int GEO_CITY = 200;
    private static final int CAT_SUBCATEGORY = 300;
    private static final int CAT_CATEGORY = 240;
    private static final int CAT_PARENT = 150;
    private static final List<ComplaintStatus> OPEN_STATUSES = List.of(
            ComplaintStatus.ASSIGNED, ComplaintStatus.ACKNOWLEDGED,
            ComplaintStatus.IN_PROGRESS, ComplaintStatus.ON_HOLD,
            ComplaintStatus.REOPENED);

    private final ComplaintRepository complaints;
    private final AiAnalysisRepository analyses;
    private final IssueCategoryRepository categories;
    private final DepartmentIssueCategoryRepository departmentCategories;
    private final DepartmentRepository departments;
    private final ServiceAreaRepository serviceAreas;
    private final JurisdictionRepository jurisdictions;
    private final ComplaintAssignmentRepository assignments;
    private final ComplaintStatusHistoryRepository histories;
    private final RoutingDecisionRepository decisions;
    private final DepartmentAdminRepository departmentAdmins;
    private final UserRepository users;
    private final NotificationService notifications;
    private final StatusTransitionPolicy policy;
    private final SpatialMatcher spatial;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;
    private final double confidenceThreshold;
    private final Map<Severity, Duration> slas;
    private final ConcurrentHashMap<UUID, Object> complaintLocks = new ConcurrentHashMap<>();

    public RoutingEngine(
            ComplaintRepository complaints,
            AiAnalysisRepository analyses,
            IssueCategoryRepository categories,
            DepartmentIssueCategoryRepository departmentCategories,
            DepartmentRepository departments,
            ServiceAreaRepository serviceAreas,
            JurisdictionRepository jurisdictions,
            ComplaintAssignmentRepository assignments,
            ComplaintStatusHistoryRepository histories,
            RoutingDecisionRepository decisions,
            DepartmentAdminRepository departmentAdmins,
            UserRepository users,
            NotificationService notifications,
            StatusTransitionPolicy policy,
            SpatialMatcher spatial,
            ObjectMapper objectMapper,
            java.time.Clock clock,
            @Value("${civicsense.ai.confidence-threshold:0.75}") double confidenceThreshold,
            @Value("${civicsense.sla.critical-hours:4}") long criticalHours,
            @Value("${civicsense.sla.high-hours:24}") long highHours,
            @Value("${civicsense.sla.medium-hours:72}") long mediumHours,
            @Value("${civicsense.sla.low-hours:168}") long lowHours) {
        this.complaints = complaints;
        this.analyses = analyses;
        this.categories = categories;
        this.departmentCategories = departmentCategories;
        this.departments = departments;
        this.serviceAreas = serviceAreas;
        this.jurisdictions = jurisdictions;
        this.assignments = assignments;
        this.histories = histories;
        this.decisions = decisions;
        this.departmentAdmins = departmentAdmins;
        this.users = users;
        this.notifications = notifications;
        this.policy = policy;
        this.spatial = spatial;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.confidenceThreshold = confidenceThreshold;
        this.slas = new EnumMap<>(Severity.class);
        this.slas.put(Severity.CRITICAL, Duration.ofHours(criticalHours));
        this.slas.put(Severity.HIGH, Duration.ofHours(highHours));
        this.slas.put(Severity.MEDIUM, Duration.ofHours(mediumHours));
        this.slas.put(Severity.LOW, Duration.ofHours(lowHours));
    }

    /**
     * Runs routing once per complaint unless {@code force} is set. A forced run is
     * an explicit super-admin reroute and creates a new current decision.
     */
    @Transactional
    public RoutingDecision route(UUID complaintId, boolean force, String adminReason, UUID adminUserId) {
        Object lock = complaintLocks.computeIfAbsent(complaintId, ignored -> new Object());
        synchronized (lock) {
            try {
                return routeLocked(complaintId, force, adminReason, adminUserId);
            } finally {
                complaintLocks.remove(complaintId, lock);
            }
        }
    }

    /** Entry point for AFTER_COMMIT listeners, which run without an active transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RoutingDecision routeAfterCommit(UUID complaintId) {
        return route(complaintId, false, null, null);
    }

    private RoutingDecision routeLocked(UUID complaintId, boolean force, String adminReason,
                                        UUID adminUserId) {
        Complaint complaint = complaints.findById(complaintId).orElse(null);
        if (complaint == null) {
            return null;
        }
        if (!force) {
            List<RoutingDecision> existing = decisions
                    .findAllByComplaintIdOrderByCreatedAtDesc(complaintId);
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }

        Instant now = clock.instant();
        RoutingInputs inputs = routingInputs(complaint);
        List<Candidate> candidates = evaluateCandidates(complaint, inputs, now);
        DecisionPlan plan = decide(complaint, inputs, candidates);
        Actor actor = force ? Actor.SUPER_ADMIN : Actor.SYSTEM;
        String reason = force && adminReason != null && !adminReason.isBlank()
                ? adminReason
                : plan.reason();

        if (force) {
            decisions.findAllByComplaintIdOrderByCreatedAtDesc(complaintId).stream()
                    .filter(RoutingDecision::isCurrent)
                    .forEach(previous -> previous.setCurrent(false));
            // Flush the supersede before inserting the replacement so the partial
            // unique index on current decisions is never transiently violated.
            decisions.flush();
        }

        RoutingDecision decision = new RoutingDecision();
        decision.setComplaint(complaint);
        decision.setRulesetVersion(RULESET_VERSION);
        decision.setAiCategoryCode(inputs.categoryCode());
        decision.setConfidence(inputs.confidence());
        decision.setMatchedJurisdiction(inputs.matchedJurisdiction());
        decision.setLocationPointWkt(inputs.locationPointWkt());
        decision.setCandidates(writeCandidates(plan.candidates()));
        decision.setSelectedDepartment(plan.selectedDepartment());
        decision.setOutcome(plan.outcome().name());
        decision.setReason(reason);
        decision.setCurrent(true);
        decision.setCreatedAt(now);
        RoutingDecision saved = decisions.saveAndFlush(decision);

        if (plan.outcome() == RoutingOutcome.ASSIGNED) {
            applyAssignment(complaint, plan.selectedDepartment(), saved, actor, reason,
                    adminUserId, now);
        } else {
            markForHumanReview(complaint, saved, actor, reason, adminUserId, now);
        }
        return saved;
    }

    private RoutingInputs routingInputs(Complaint complaint) {
        AiAnalysis analysis = analyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaint.getId())
                .orElse(null);
        boolean usableAnalysis = analysis != null
                && analysis.getStatus() == AiAnalysisStatus.SUCCESS
                && analysis.getSource() != null;
        boolean adminOverride = usableAnalysis && analysis.getSource() == AiAnalysisSource.ADMIN_OVERRIDE;

        Double confidence = usableAnalysis ? analysis.getConfidence() : null;
        if (adminOverride) {
            confidence = 1.0;
        }
        boolean requiresReview = !usableAnalysis
                || analysis.isRequiresHumanReview()
                || Boolean.TRUE.equals(analysis.getIsCivicIssue()) == false
                || confidence == null;

        String categoryCode = usableAnalysis ? analysis.getCategoryCode() : null;
        String subcategoryCode = usableAnalysis ? analysis.getSubcategoryCode() : null;
        boolean codesValid = true;
        IssueCategory category = null;
        IssueCategory subcategory = null;
        if (categoryCode != null && !categoryCode.isBlank()) {
            category = categories.findByCode(categoryCode.trim().toUpperCase(Locale.ROOT)).orElse(null);
        }
        if (subcategoryCode != null && !subcategoryCode.isBlank()) {
            subcategory = categories.findByCode(subcategoryCode.trim().toUpperCase(Locale.ROOT)).orElse(null);
        }
        if (category == null || !category.isActive()) {
            codesValid = false;
            category = complaint.getCategory();
            subcategory = complaint.getSubcategory();
        }
        if (subcategory == null || !subcategory.isActive()
                || (category != null && subcategory.getParentCategory() == null)
                || (category != null && !subcategory.getParentCategory().getId().equals(category.getId()))) {
            subcategory = null;
        }
        if (category == null || !category.isActive() || subcategory == null) {
            codesValid = false;
        }

        // Without a usable AI result, absence of evidence is not evidence of a
        // non-civic issue: route to LOW_CONFIDENCE human review instead.
        boolean civic = !usableAnalysis || adminOverride
                || Boolean.TRUE.equals(analysis.getIsCivicIssue());
        boolean lowConfidence = !adminOverride
                && (confidence == null || confidence < confidenceThreshold);

        String locationPointWkt = null;
        boolean validCoordinates = validCoordinates(complaint);
        if (validCoordinates) {
            locationPointWkt = "POINT(" + complaint.getLongitude() + " " + complaint.getLatitude() + ")";
        }

        UUID jurisdictionId = null;
        if (validCoordinates) {
            List<UUID> matched = spatial.matchingJurisdictionIds(
                    complaint.getLongitude(), complaint.getLatitude());
            if (!matched.isEmpty()) {
                jurisdictionId = matched.get(0);
            }
        }
        if (jurisdictionId == null && complaint.getJurisdiction() != null) {
            jurisdictionId = complaint.getJurisdiction().getId();
        }
        Jurisdiction matchedJurisdiction = jurisdictionId == null
                ? null
                : jurisdictions.findById(jurisdictionId).orElse(null);

        return new RoutingInputs(category, subcategory, categoryCode, confidence, requiresReview,
                civic, lowConfidence, codesValid, validCoordinates, locationPointWkt,
                matchedJurisdiction, analysis);
    }

    private List<Candidate> evaluateCandidates(Complaint complaint, RoutingInputs inputs, Instant now) {
        if (!inputs.validCoordinates() || !inputs.codesValid()) {
            return List.of();
        }
        Map<UUID, Integer> categoryScores = categoryScores(inputs);
        if (categoryScores.isEmpty()) {
            return List.of();
        }
        Set<UUID> polygonAreas = new HashSet<>(spatial.matchingServiceAreaIds(
                complaint.getLongitude(), complaint.getLatitude()));
        Map<UUID, List<com.civicsense.domain.ServiceArea>> areasByDepartment = new HashMap<>();
        serviceAreas.findAllByActiveTrue().stream()
                .filter(area -> validOn(area, now))
                .forEach(area -> areasByDepartment
                        .computeIfAbsent(area.getDepartment().getId(), ignored -> new ArrayList<>())
                        .add(area));

        List<Candidate> candidates = new ArrayList<>();
        for (Department department : departments.findAll()) {
            if (department.getVerificationStatus() != VerificationStatus.VERIFIED
                    || !department.isActive()
                    || !department.isAcceptsComplaints()
                    || !department.isAutoAssignEnabled()) {
                continue;
            }
            List<com.civicsense.domain.ServiceArea> departmentAreas = areasByDepartment
                    .getOrDefault(department.getId(), List.of());
            GeoMatch geo = geoMatch(department, complaint, departmentAreas, polygonAreas);
            if (geo.score() == 0) {
                continue;
            }
            int catScore = categoryScores.getOrDefault(department.getId(), 0);
            if (catScore == 0) {
                continue;
            }
            boolean atCapacity = atCapacity(department);
            int total = geo.score() + catScore + department.getRoutingPriority();
            List<String> reasons = new ArrayList<>();
            reasons.add(geo.reason());
            reasons.add("category capability " + catScore);
            if (atCapacity) {
                reasons.add("department at capacity");
            }
            candidates.add(new Candidate(department, geo.score(), geo.match(), catScore,
                    catScoreName(catScore), department.getRoutingPriority(),
                    total, atCapacity, List.copyOf(reasons)));
        }
        return candidates;
    }

    private Map<UUID, Integer> categoryScores(RoutingInputs inputs) {
        Map<UUID, Integer> scores = new HashMap<>();
        if (inputs.subcategory() != null) {
            scores.put(inputs.subcategory().getId(), CAT_SUBCATEGORY);
        }
        if (inputs.category() != null) {
            scores.put(inputs.category().getId(), CAT_CATEGORY);
            IssueCategory parent = inputs.category().getParentCategory();
            if (parent != null && parent.isActive()) {
                scores.put(parent.getId(), CAT_PARENT);
            }
        }
        if (scores.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> result = new HashMap<>();
        for (UUID categoryId : scores.keySet()) {
            for (DepartmentIssueCategory capability : departmentCategories.findAllByIdCategoryId(categoryId)) {
                UUID departmentId = capability.getId().getDepartmentId();
                result.merge(departmentId, scores.get(categoryId), Math::max);
            }
        }
        return result;
    }

    private GeoMatch geoMatch(Department department, Complaint complaint,
                              List<com.civicsense.domain.ServiceArea> areas,
                              Set<UUID> polygonAreas) {
        int score = 0;
        String match = "NONE";
        String reason = "no geographic coverage";
        for (com.civicsense.domain.ServiceArea area : areas) {
            if (polygonAreas.contains(area.getId()) && score < GEO_POLYGON) {
                score = GEO_POLYGON;
                match = "POLYGON";
                reason = "service-area polygon contains point";
            }
            if (matches(complaint.getWard(), area.getCoversWard()) && score < GEO_WARD) {
                score = GEO_WARD;
                match = "WARD";
                reason = "ward " + complaint.getWard();
            }
            if (matches(complaint.getZone(), area.getCoversZone()) && score < GEO_ZONE) {
                score = GEO_ZONE;
                match = "ZONE";
                reason = "zone " + complaint.getZone();
            }
        }
        // Weakest fallback is the department's registered city, as specified in
        // AI_ROUTING_DESIGN.md §5.2. It never overrides a more specific area match.
        if (matches(complaint.getCity(), department.getCity()) && score < GEO_CITY) {
            score = GEO_CITY;
            match = "CITY";
            reason = "city " + department.getCity();
        }
        return new GeoMatch(score, match, reason);
    }

    private DecisionPlan decide(Complaint complaint, RoutingInputs inputs, List<Candidate> candidates) {
        String reviewReason = reviewReason(inputs);
        if (reviewReason != null) {
            return new DecisionPlan(RoutingOutcome.valueOf(reviewReason.split(":", 2)[0].trim()),
                    reviewReason.substring(reviewReason.indexOf(':') + 1).trim(), null, candidates);
        }
        if (!inputs.validCoordinates()) {
            return new DecisionPlan(RoutingOutcome.UNASSIGNED,
                    "Routing skipped: complaint coordinates are missing or invalid.", null, candidates);
        }
        if (!inputs.codesValid()) {
            return new DecisionPlan(RoutingOutcome.UNASSIGNED,
                    "Routing skipped: category or subcategory could not be validated.", null, candidates);
        }
        if (candidates.isEmpty()) {
            return new DecisionPlan(RoutingOutcome.UNASSIGNED,
                    "No verified, active department has both category capability and valid geographic coverage.",
                    null, candidates);
        }

        // Capacity is evaluated only for the deterministic winner. A lower-scored
        // department must never inherit a full department's work by default.
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::total).reversed()
                        .thenComparing(candidate -> candidate.departmentCreatedAt() == null
                                ? Instant.MAX
                                : candidate.departmentCreatedAt())
                        .thenComparing(Candidate::departmentId))
                .toList();
        Candidate winner = ordered.get(0);
        if (ordered.size() > 1 && ordered.get(1).total() == winner.total()
                && !ordered.get(1).departmentId().equals(winner.departmentId())) {
            return new DecisionPlan(RoutingOutcome.AMBIGUOUS,
                    "Two departments have the same top score; super admin must choose.",
                    null, candidates);
        }
        if (winner.atCapacity()) {
            return new DecisionPlan(RoutingOutcome.NO_CAPACITY,
                    "The highest-scoring department is at capacity.", null, candidates);
        }
        return new DecisionPlan(RoutingOutcome.ASSIGNED,
                "Single highest-scoring eligible department (total " + winner.total() + ").",
                departments.getReferenceById(winner.departmentId()), candidates);
    }

    private String reviewReason(RoutingInputs inputs) {
        if (!inputs.civic()) {
            return "NON_CIVIC:AI analysis determined this is not a civic issue; super-admin review required.";
        }
        if (inputs.lowConfidence() || inputs.requiresReview()) {
            return "LOW_CONFIDENCE:AI confidence or review flag requires super-admin review before assignment.";
        }
        return null;
    }

    private void applyAssignment(Complaint complaint, Department target, RoutingDecision decision,
                                 Actor actor, String reason, UUID adminUserId, Instant now) {
        policy.validate(complaint.getStatus(), ComplaintStatus.ASSIGNED, actor);
        assignments.findByComplaintIdAndActiveTrue(complaint.getId()).ifPresent(active -> {
            active.setActive(false);
            active.setUnassignedAt(now);
        });
        ComplaintAssignment assignment = new ComplaintAssignment();
        assignment.setComplaint(complaint);
        assignment.setDepartment(target);
        assignment.setAssignmentType(forceType(actor));
        assignment.setPreviousDepartment(complaint.getRelatedDepartment());
        if (adminUserId != null) {
            assignment.setAssignedBy(users.getReferenceById(adminUserId));
        }
        assignment.setRoutingDecisionId(decision.getId());
        assignment.setReason(reason);
        assignment.setActive(true);
        assignment.setAssignedAt(now);
        assignment.setCreatedAt(now);
        assignments.save(assignment);

        complaint.setRelatedDepartment(target);
        complaint.setAssignedAt(now);
        complaint.setUpdatedAt(now);
        Severity severity = resolveSeverity(decision, complaint);
        complaint.setSeverity(severity);
        complaint.setResolutionDueAt(now.plus(slas.get(severity)));
        changeStatus(complaint, decision, ComplaintStatus.ASSIGNED, reason, actor, adminUserId, now);

        User citizen = users.getReferenceById(complaint.getCitizen().getId());
        notifications.notify(citizen.getId(), complaint.getId(), NotificationType.COMPLAINT_ASSIGNED,
                "Complaint assigned", complaint.getComplaintNumber() + " was assigned to "
                        + target.getName() + ".");
        departmentAdmins.findByDepartmentId(target.getId()).forEach(admin ->
                notifications.notify(admin.getUserId(), complaint.getId(),
                        NotificationType.COMPLAINT_ASSIGNED, "Complaint assigned",
                        complaint.getComplaintNumber() + " was routed to your department."));
    }

    private void markForHumanReview(Complaint complaint, RoutingDecision decision,
                                    Actor actor, String reason, UUID adminUserId, Instant now) {
        assignments.findByComplaintIdAndActiveTrue(complaint.getId()).ifPresent(active -> {
            active.setActive(false);
            active.setUnassignedAt(now);
        });
        complaint.setRelatedDepartment(null);
        complaint.setAssignedAt(null);
        // Preserve the Phase 5 AI lifecycle status for first-pass review. A forced
        // admin reroute that removes an assignment must move back to the queue.
        if (actor == Actor.SUPER_ADMIN && complaint.getStatus() == ComplaintStatus.ASSIGNED) {
            changeStatus(complaint, decision, ComplaintStatus.PENDING_ASSIGNMENT,
                    reason, actor, adminUserId, now);
        }
        users.findByRole(com.civicsense.domain.Role.SUPER_ADMIN).forEach(admin ->
                notifications.notify(admin.getId(), complaint.getId(),
                        NotificationType.UNASSIGNED_COMPLAINT, "Complaint needs assignment",
                        complaint.getComplaintNumber() + ": " + reason));
    }

    private void changeStatus(Complaint complaint, RoutingDecision decision, ComplaintStatus target,
                              String reason, Actor actor, UUID adminUserId, Instant now) {
        ComplaintStatus from = complaint.getStatus();
        if (from == target) {
            return;
        }
        policy.validate(from, target, actor);
        if (policy.requiresReason(from, target) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Reason required for " + from + " -> " + target);
        }
        complaint.setStatus(target);
        complaint.setUpdatedAt(now);
        complaints.save(complaint);
        ComplaintStatusHistory history = new ComplaintStatusHistory();
        history.setComplaint(complaint);
        history.setFromStatus(from);
        history.setToStatus(target);
        history.setActorRole(actor.name());
        history.setReason(reason);
        if (adminUserId != null) {
            history.setChangedBy(users.getReferenceById(adminUserId));
        }
        history.setMetadata("{\"routingDecisionId\":\"" + decision.getId() + "\"}");
        history.setCreatedAt(now);
        histories.save(history);
    }

    private static AssignmentType forceType(Actor actor) {
        return actor == Actor.SUPER_ADMIN ? AssignmentType.ADMIN_CORRECTION : AssignmentType.AUTO;
    }

    private Severity resolveSeverity(RoutingDecision decision, Complaint complaint) {
        AiAnalysis analysis = analyses.findFirstByComplaintIdOrderByCreatedAtDesc(complaint.getId())
                .orElse(null);
        if (analysis != null && analysis.getSeverity() != null) {
            try {
                return Severity.valueOf(analysis.getSeverity().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to the complaint's existing severity.
            }
        }
        return complaint.getSeverity();
    }

    private boolean atCapacity(Department department) {
        Integer max = department.getMaxOpenComplaints();
        if (max == null) {
            return false; // null means no configured cap
        }
        return complaints.countByRelatedDepartmentIdAndStatusIn(
                department.getId(), OPEN_STATUSES) >= max;
    }

    private static boolean validOn(com.civicsense.domain.ServiceArea area, Instant now) {
        return (area.getValidFrom() == null || !area.getValidFrom().isAfter(now))
                && (area.getValidUntil() == null || !area.getValidUntil().isBefore(now));
    }

    private static boolean matches(String complaintValue, String coverageValue) {
        return complaintValue != null && coverageValue != null
                && complaintValue.trim().equalsIgnoreCase(coverageValue.trim());
    }

    static boolean validCoordinates(Complaint complaint) {
        Double latitude = complaint.getLatitude();
        Double longitude = complaint.getLongitude();
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }
        return complaint.getLocationSource() == null
                || complaint.getLocationSource() == LocationSource.GPS
                || complaint.getLocationSource() == LocationSource.MANUAL
                || complaint.getLocationSource() == LocationSource.GEOCODED;
    }

    private static String catScoreName(int catScore) {
        return switch (catScore) {
            case CAT_SUBCATEGORY -> "SUBCATEGORY";
            case CAT_CATEGORY -> "CATEGORY";
            case CAT_PARENT -> "PARENT";
            default -> "NONE";
        };
    }

    private String writeCandidates(List<Candidate> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    public record Candidate(UUID departmentId, String departmentName, int geoScore, String geoMatch,
                            int catScore, String catMatch, int priority, int total,
                            boolean atCapacity, List<String> reasons, Instant departmentCreatedAt) {
        Candidate(Department department, int geoScore, String geoMatch, int catScore, String catMatch,
                  int priority, int total, boolean atCapacity, List<String> reasons) {
            this(department.getId(), department.getName(), geoScore, geoMatch, catScore, catMatch,
                    priority, total, atCapacity, reasons, department.getCreatedAt());
        }
    }

    private record GeoMatch(int score, String match, String reason) {
    }

    private record DecisionPlan(RoutingOutcome outcome, String reason, Department selectedDepartment,
                                List<Candidate> candidates) {
    }

    private record RoutingInputs(IssueCategory category, IssueCategory subcategory, String categoryCode,
                                 Double confidence, boolean requiresReview, boolean civic,
                                 boolean lowConfidence, boolean codesValid, boolean validCoordinates,
                                 String locationPointWkt, Jurisdiction matchedJurisdiction,
                                 AiAnalysis analysis) {
    }

}
