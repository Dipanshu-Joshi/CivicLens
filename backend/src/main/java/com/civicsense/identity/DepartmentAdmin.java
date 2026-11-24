package com.civicsense.identity;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.domain.AdminLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "department_admins")
public class DepartmentAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_level", nullable = false)
    private AdminLevel adminLevel = AdminLevel.OWNER;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAdmin = true;

    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public AdminLevel getAdminLevel() {
        return adminLevel;
    }

    public void setAdminLevel(AdminLevel adminLevel) {
        this.adminLevel = adminLevel;
    }

    public boolean isPrimaryAdmin() {
        return primaryAdmin;
    }

    public void setPrimaryAdmin(boolean primaryAdmin) {
        this.primaryAdmin = primaryAdmin;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
