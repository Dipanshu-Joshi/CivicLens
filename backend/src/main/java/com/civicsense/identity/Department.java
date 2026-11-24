package com.civicsense.identity;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.domain.VerificationStatus;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "department_code", nullable = false, unique = true)
    private String departmentCode;

    @Column(name = "department_type", nullable = false)
    private String departmentType;

    private String description;

    @Column(name = "official_email", nullable = false)
    private String officialEmail;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "office_address")
    private String officeAddress;

    private String city;
    private String state;
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING_VERIFICATION;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "accepts_complaints", nullable = false)
    private boolean acceptsComplaints;

    @Column(name = "auto_assign_enabled", nullable = false)
    private boolean autoAssignEnabled = true;

    @Column(name = "routing_priority", nullable = false)
    private int routingPriority = 100;

    @Column(name = "max_open_complaints")
    private Integer maxOpenComplaints;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDepartmentType() {
        return departmentType;
    }

    public void setDepartmentType(String departmentType) {
        this.departmentType = departmentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOfficialEmail() {
        return officialEmail;
    }

    public void setOfficialEmail(String officialEmail) {
        this.officialEmail = officialEmail;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getOfficeAddress() {
        return officeAddress;
    }

    public void setOfficeAddress(String officeAddress) {
        this.officeAddress = officeAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getMaxOpenComplaints() {
        return maxOpenComplaints;
    }

    public void setMaxOpenComplaints(Integer maxOpenComplaints) {
        this.maxOpenComplaints = maxOpenComplaints;
    }

    public boolean isAcceptsComplaints() {
        return acceptsComplaints;
    }

    public void setAcceptsComplaints(boolean acceptsComplaints) {
        this.acceptsComplaints = acceptsComplaints;
    }

    public boolean isAutoAssignEnabled() {
        return autoAssignEnabled;
    }

    public void setAutoAssignEnabled(boolean autoAssignEnabled) {
        this.autoAssignEnabled = autoAssignEnabled;
    }

    public int getRoutingPriority() {
        return routingPriority;
    }

    public void setRoutingPriority(int routingPriority) {
        this.routingPriority = routingPriority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public UUID getVerifiedByUserId() {
        return verifiedByUserId;
    }

    public void setVerifiedByUserId(UUID verifiedByUserId) {
        this.verifiedByUserId = verifiedByUserId;
    }
}
