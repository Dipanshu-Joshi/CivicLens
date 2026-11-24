package com.civicsense.department.exception;

/** Validation errors for department-controlled settings. */
public final class DepartmentExceptions {

    private DepartmentExceptions() {
    }

    public static class InvalidDepartmentSettingException extends RuntimeException {
        public InvalidDepartmentSettingException(String detail) {
            super(detail);
        }
    }
}
