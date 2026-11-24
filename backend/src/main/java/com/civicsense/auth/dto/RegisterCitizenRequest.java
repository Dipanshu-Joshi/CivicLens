package com.civicsense.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCitizenRequest(

        @NotBlank @Size(max = 150) String fullName,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Size(min = 10, max = 100)
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{10,}$",
                message = "password must contain upper and lower case letters, a digit and a special character")
        String password,

        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "phone must be in E.164-like format")
        String phone,

        @Size(max = 10) String locale) {
}
