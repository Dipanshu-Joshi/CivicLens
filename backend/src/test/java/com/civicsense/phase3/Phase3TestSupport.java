package com.civicsense.phase3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.IssueCategory;
import com.civicsense.domain.Jurisdiction;
import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.DepartmentRepository;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;
import com.civicsense.repository.AiAnalysisRepository;
import com.civicsense.repository.AiAnalysisRunRepository;
import com.civicsense.repository.AuditLogRepository;
import com.civicsense.repository.ComplaintAssignmentRepository;
import com.civicsense.repository.ComplaintCommentRepository;
import com.civicsense.repository.ComplaintFeedbackRepository;
import com.civicsense.repository.ComplaintImageRepository;
import com.civicsense.repository.ComplaintInternalNoteRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.ComplaintStatusHistoryRepository;
import com.civicsense.repository.IssueCategoryRepository;
import com.civicsense.repository.JurisdictionRepository;
import com.civicsense.repository.NotificationPreferenceRepository;
import com.civicsense.repository.NotificationRepository;
import com.civicsense.repository.OutboxEventRepository;
import com.civicsense.repository.PasswordResetTokenRepository;
import com.civicsense.repository.ReassignmentRequestRepository;
import com.civicsense.repository.RoutingDecisionRepository;
import com.civicsense.repository.ServiceAreaRepository;
import com.civicsense.security.AuthenticatedUser;
import com.civicsense.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared seed data and helpers for Phase 3 integration tests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class Phase3TestSupport {

    protected static final String BASE = "";
    protected static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected DepartmentRepository departments;

    @Autowired
    protected com.civicsense.identity.DepartmentAdminRepository departmentAdmins;

    @Autowired
    protected IssueCategoryRepository categories;

    @Autowired
    protected JurisdictionRepository jurisdictions;

    @Autowired
    protected ServiceAreaRepository serviceAreas;

    @Autowired
    protected RoutingDecisionRepository routingDecisions;

    @Autowired
    protected ComplaintRepository complaints;

    @Autowired
    protected ComplaintStatusHistoryRepository statusHistory;

    @Autowired
    protected ComplaintAssignmentRepository assignments;

    @Autowired
    protected ComplaintImageRepository images;

    @Autowired
    protected ComplaintCommentRepository comments;

    @Autowired
    protected ComplaintInternalNoteRepository internalNotes;

    @Autowired
    protected ComplaintFeedbackRepository feedback;

    @Autowired
    protected ReassignmentRequestRepository reassignmentRequests;

    @Autowired
    protected NotificationRepository notifications;

    @Autowired
    protected OutboxEventRepository outboxEvents;

    @Autowired
    protected NotificationPreferenceRepository notificationPreferences;

    @Autowired
    protected AiAnalysisRepository aiAnalyses;

    @Autowired
    protected AiAnalysisRunRepository aiAnalysisRuns;

    @Autowired
    protected AuditLogRepository auditLogs;

    @Autowired
    protected com.civicsense.identity.RefreshTokenRepository refreshTokens;

    @Autowired
    protected PasswordResetTokenRepository passwordResetTokens;

    protected User citizenA;
    protected User citizenB;
    protected User deptAdminA;
    protected User deptAdminB;
    protected User superAdmin;
    protected Department deptA;
    protected Department deptB;
    protected Department unverifiedDept;
    protected IssueCategory roadCategory;
    protected IssueCategory potholeCategory;
    protected Jurisdiction ward12;
    protected String citizenAToken;
    protected String citizenBToken;
    protected String deptAToken;
    protected String deptBToken;
    protected String superAdminToken;

    protected void resetAndSeed() {
        auditLogs.deleteAllInBatch();
        notifications.deleteAllInBatch();
        outboxEvents.deleteAllInBatch();
        notificationPreferences.deleteAllInBatch();
        feedback.deleteAllInBatch();
        internalNotes.deleteAllInBatch();
        comments.deleteAllInBatch();
        reassignmentRequests.deleteAllInBatch();
        statusHistory.deleteAllInBatch();
        assignments.deleteAllInBatch();
        routingDecisions.deleteAllInBatch();
        images.deleteAllInBatch();
        aiAnalysisRuns.deleteAllInBatch();
        aiAnalyses.deleteAllInBatch();
        complaints.deleteAllInBatch();
        serviceAreas.deleteAllInBatch();
        categories.deleteAllInBatch();
        jurisdictions.deleteAllInBatch();
        departmentAdmins.deleteAllInBatch();
        departments.deleteAllInBatch();
        passwordResetTokens.deleteAllInBatch();
        refreshTokens.deleteAllInBatch();
        users.deleteAllInBatch();

        deptA = department("Road Maintenance", "ROAD-A", VerificationStatus.VERIFIED, true);
        deptB = department("Water Works", "WATER-B", VerificationStatus.VERIFIED, true);
        unverifiedDept = department("Unverified Dept", "UNV-C", VerificationStatus.PENDING_VERIFICATION, false);

        citizenA = user("p3-citizen-a@example.com", Role.CITIZEN, null);
        citizenB = user("p3-citizen-b@example.com", Role.CITIZEN, null);
        deptAdminA = user("p3-admin-a@example.com", Role.DEPARTMENT_ADMIN, deptA);
        deptAdminB = user("p3-admin-b@example.com", Role.DEPARTMENT_ADMIN, deptB);
        superAdmin = user("p3-super@example.com", Role.SUPER_ADMIN, null);

        roadCategory = category("ROAD", "Roads", null);
        potholeCategory = category("POTHOLE", "Pothole", roadCategory);
        ward12 = jurisdiction("WARD-12", "Ward 12");

        citizenAToken = token(citizenA, null);
        citizenBToken = token(citizenB, null);
        deptAToken = token(deptAdminA, deptA);
        deptBToken = token(deptAdminB, deptB);
        superAdminToken = token(superAdmin, null);
    }

    private User user(String email, Role role, Department department) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("unused-hash");
        user.setRole(role);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFullName(email.substring(0, email.indexOf('@')));
        User saved = users.save(user);
        if (department != null) {
            DepartmentAdmin admin = new DepartmentAdmin();
            admin.setUserId(saved.getId());
            admin.setDepartmentId(department.getId());
            admin.setPrimaryAdmin(true);
            departmentAdmins.save(admin);
        }
        return saved;
    }

    private Department department(String name, String code, VerificationStatus status,
            boolean acceptsComplaints) {
        Department department = new Department();
        department.setName(name);
        department.setDepartmentCode(code);
        department.setDepartmentType("MUNICIPAL");
        department.setOfficialEmail(code.toLowerCase() + "@example.com");
        department.setVerificationStatus(status);
        department.setActive(true);
        department.setAcceptsComplaints(acceptsComplaints);
        department.setVerifiedAt(status == VerificationStatus.VERIFIED ? java.time.Instant.now() : null);
        return departments.save(department);
    }

    private IssueCategory category(String code, String name, IssueCategory parent) {
        IssueCategory category = new IssueCategory();
        category.setCode(code);
        category.setName(name);
        category.setParentCategory(parent);
        category.setDisplayOrder(code.equals("ROAD") ? 1 : 2);
        category.setActive(true);
        return categories.save(category);
    }

    private Jurisdiction jurisdiction(String code, String name) {
        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setCode(code);
        jurisdiction.setName(name);
        jurisdiction.setLevel("WARD");
        jurisdiction.setActive(true);
        return jurisdictions.save(jurisdiction);
    }

    protected String token(User user, Department department) {
        return jwtService.generateAccessToken(new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getRole(),
                department == null ? null : department.getId(),
                department == null ? null : department.getVerificationStatus()));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Creates a complaint through the citizen API and returns its id. */
    protected UUID submitComplaint(String token, String description) throws Exception {
        String body = json(new java.util.LinkedHashMap<String, Object>() {{
            put("description", description);
            put("latitude", 28.6139);
            put("longitude", 77.2090);
            put("locationSource", "GPS");
            put("addressLine", "12 Main Street");
            put("city", "New Delhi");
            put("ward", "WARD-12");
            put("categoryCode", "ROAD");
            put("subcategoryCode", "POTHOLE");
        }});
        String response = mockMvc.perform(post(BASE + "/citizen/complaints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
