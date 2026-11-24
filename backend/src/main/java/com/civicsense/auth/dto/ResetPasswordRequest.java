package com.civicsense.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank @Size(max = 500) String token,

        @NotBlank
        @Size(min = 10, max = 100)
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{10,}$",
                message = "password must contain upper and lower case letters, a digit and a special character")
        String password) {
}
