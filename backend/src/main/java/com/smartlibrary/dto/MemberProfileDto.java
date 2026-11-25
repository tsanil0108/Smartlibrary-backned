package com.smartlibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MemberProfileDto {

    private MemberProfileDto() {
    }

    public record ProfileResponse(
            Long id,
            String name,
            String email,
            String role,
            String status
    ) {
    }

    public record UpdateProfileRequest(

            @NotBlank(message = "Name is required")
            @Size(min = 2, max = 100)
            String name

    ) {
    }

    public record ChangePasswordRequest(

            @NotBlank(message = "Current password is required")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(
                    min = 8,
                    max = 72,
                    message = "Password must contain 8 to 72 characters"
            )
            String newPassword,

            @NotBlank(message = "Confirm password is required")
            String confirmPassword

    ) {
    }

    public record MessageResponse(
            String message
    ) {
    }
}