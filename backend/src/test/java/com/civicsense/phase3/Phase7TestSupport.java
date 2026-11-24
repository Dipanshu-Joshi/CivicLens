package com.civicsense.phase3;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.civicsense.domain.AccountStatus;
import com.civicsense.domain.AdminLevel;
import com.civicsense.domain.ComplaintImage;
import com.civicsense.domain.ImageKind;
import com.civicsense.domain.Role;
import com.civicsense.domain.UploadStatus;
import com.civicsense.identity.Department;
import com.civicsense.identity.DepartmentAdmin;
import com.civicsense.identity.User;
import com.civicsense.repository.DepartmentIssueCategoryRepository;

/**
 * Extra fixtures for Phase 7 tests. Kept in the Phase 3 package so the shared
 * {@link Phase3TestSupport} seed helper can be reused unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class Phase7TestSupport extends Phase3TestSupport {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected DepartmentIssueCategoryRepository departmentCategories;

    /** Super-admin assignment via the real API, including history and notifications. */
    protected void assign(UUID complaintId, UUID departmentId) throws Exception {
        mockMvc.perform(post(BASE + "/admin/complaints/" + complaintId + "/assign")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentId", departmentId.toString(),
                                "reason", "Routed to the verified responsible department"))))
                .andExpect(status().isOk());
    }

    /** Direct, controlled row edits for timestamps/dimensions not exposed by citizen APIs. */
    protected void setComplaintColumn(UUID complaintId, String column, Object value) {
        jdbc.update("update complaints set " + column + " = ? where id = ?", value, complaintId);
    }

    protected void setComplaintInstant(UUID complaintId, String column, Instant value) {
        setComplaintColumn(complaintId, column, Timestamp.from(value));
    }

    protected User createUser(String email, Role role, Department department,
            AdminLevel adminLevel, boolean primary) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("unused-hash");
        user.setRole(role);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setFullName(email.substring(0, email.indexOf('@')));
        User saved = users.save(user);
        if (department != null) {
            DepartmentAdmin membership = new DepartmentAdmin();
            membership.setUserId(saved.getId());
            membership.setDepartmentId(department.getId());
            membership.setAdminLevel(adminLevel);
            membership.setPrimaryAdmin(primary);
            departmentAdmins.save(membership);
        }
        return saved;
    }

    /** Direct staged-image fixture; bypasses storage because authorization is the subject. */
    protected UUID stageImage(UUID ownerId, ImageKind kind, UploadStatus status, UUID complaintId) {
        ComplaintImage image = new ComplaintImage();
        image.setStorageKey("uploads/" + ownerId + "/" + UUID.randomUUID() + ".png");
        image.setOriginalFilename("evidence.png");
        image.setContentType("image/png");
        image.setSizeBytes(100L);
        image.setImageKind(kind);
        image.setUploadStatus(status);
        image.setUploadedBy(users.getReferenceById(ownerId));
        image.setCreatedAt(Instant.now());
        if (complaintId != null) {
            image.setComplaint(complaints.getReferenceById(complaintId));
        }
        if (status == UploadStatus.VERIFIED) {
            image.setWidth(2);
            image.setHeight(2);
            image.setChecksumSha256("checksum-" + UUID.randomUUID());
            image.setPhash(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }
        return images.save(image).getId();
    }

    protected void setDepartmentActive(UUID departmentId, boolean active) {
        Department department = departments.findById(departmentId).orElseThrow();
        department.setActive(active);
        departments.save(department);
    }

    protected String tokenFor(User user, Department department) {
        return token(user, department);
    }
}
