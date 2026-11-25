package com.smartlibrary.dto;

import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class UserDto {
    private UserDto() {}

    public record UserResponse(Long id, String name, String email, Role role, UserStatus status, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt());
        }
    }

    public record UpdateStatusRequest(@NotNull UserStatus status) {}
}
