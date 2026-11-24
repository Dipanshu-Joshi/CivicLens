package com.civicsense.domain;

/** All authorization decisions are made in Spring Boot using this enum. */
public enum Role {
    CITIZEN,
    DEPARTMENT_ADMIN,
    SUPER_ADMIN
}
