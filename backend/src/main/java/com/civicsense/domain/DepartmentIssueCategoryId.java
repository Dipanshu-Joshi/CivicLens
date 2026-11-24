package com.civicsense.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class DepartmentIssueCategoryId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    public DepartmentIssueCategoryId(UUID departmentId, UUID categoryId) {
        this.departmentId = departmentId;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DepartmentIssueCategoryId that)) {
            return false;
        }
        return Objects.equals(departmentId, that.departmentId)
                && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departmentId, categoryId);
    }
}
