package com.civicsense.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phase 2 scope: department identity and owner administrator. Category codes
 * and service-area GeoJSON from the full Phase 0 contract are captured in
 * Phase 3, when the geometry pipeline and PostGIS verification exist.
 */
public record RegisterDepartmentRequest(

        @Valid @NotNull Department department,

        @Valid @NotNull Administrator administrator) {

    public record Department(

            @NotBlank @Size(max = 200) String name,

            @NotBlank @Size(max = 80) String departmentType,

            @NotBlank @Email @Size(max = 255) String officialEmail,

            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "contactNumber must be in E.164-like format")
            String contactNumber,

            @Size(max = 2000) String description,

            @Size(max = 255) String officeAddress,

            @Size(max = 120) String city,

            @Size(max = 120) String state,

            @Size(max = 120) String country) {
    }

    public record Administrator(

            @NotBlank @Size(max = 150) String fullName,

            @NotBlank @Email @Size(max = 255) String email,

            @NotBlank
            @Size(min = 10, max = 100)
            @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{10,}$",
                    message = "password must contain upper and lower case letters, a digit and a special character")
            String password,

            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "phone must be in E.164-like format")
            String phone) {
    }
}
