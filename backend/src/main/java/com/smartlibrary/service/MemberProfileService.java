package com.smartlibrary.service;

import com.smartlibrary.dto.MemberProfileDto.ProfileResponse;
import com.smartlibrary.dto.MemberProfileDto.UpdateProfileRequest;
import com.smartlibrary.dto.MemberProfileDto.ChangePasswordRequest;
import com.smartlibrary.dto.MemberProfileDto.MessageResponse;

import com.smartlibrary.entity.User;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.UserStatus;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.UserRepository;

import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuditService auditService;

    // ==========================================
    // GET MEMBER PROFILE
    // ==========================================

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(
                        () -> new NotFoundException("User", userId)
                );

        validateMember(user);

        return toResponse(user);
    }

    // ==========================================
    // UPDATE MEMBER PROFILE
    // ==========================================

    @Transactional
    public ProfileResponse updateProfile(
            Long userId,
            UpdateProfileRequest request
    ) {

        User user = getMemberForUpdate(userId);

        String name = request.name().trim();

        if (name.length() < 2) {

            throw new BusinessRuleException(
                    "INVALID_NAME",
                    "Name must contain at least 2 characters"
            );
        }

        user.setName(name);

        userRepository.save(user);

        auditService.log(
                userId,
                "PROFILE_UPDATED",
                "USER",
                userId,
                "Member profile updated"
        );

        return toResponse(user);
    }

    // ==========================================
    // CHANGE PASSWORD
    // ==========================================

    @Transactional
    public MessageResponse changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {

        User user = getMemberForUpdate(userId);

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {

            throw new BusinessRuleException(
                    "INVALID_CURRENT_PASSWORD",
                    "Current password is incorrect"
            );
        }

        if (!request.newPassword().equals(
                request.confirmPassword()
        )) {

            throw new BusinessRuleException(
                    "PASSWORD_MISMATCH",
                    "New password and confirm password do not match"
            );
        }

        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new BusinessRuleException(
                    "PASSWORD_TOO_LONG",
                    "Password exceeds the BCrypt 72-byte limit"
            );
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash()
        )) {

            throw new BusinessRuleException(
                    "SAME_PASSWORD",
                    "New password must be different from current password"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(
                        request.newPassword()
                )
        );

        userRepository.save(user);

        auditService.log(
                userId,
                "PASSWORD_CHANGED",
                "USER",
                userId,
                "Member password changed successfully"
        );

        return new MessageResponse(
                "Password changed successfully"
        );
    }

    // ==========================================
    // GET MEMBER WITH DATABASE LOCK
    // ==========================================

    private User getMemberForUpdate(Long userId) {

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(
                        () -> new NotFoundException("User", userId)
                );

        validateMember(user);

        return user;
    }

    // ==========================================
    // VALIDATE MEMBER
    // ==========================================

    private void validateMember(User user) {

        if (user.getRole() != Role.MEMBER) {

            throw new BusinessRuleException(
                    "NOT_A_MEMBER",
                    "Only members can access this feature"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Your account is not active"
            );
        }
    }

    // ==========================================
    // RESPONSE MAPPER
    // ==========================================

    private ProfileResponse toResponse(User user) {

        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name()
        );
    }
}