package com.smartlibrary.controller;

import com.smartlibrary.dto.MemberProfileDto.ProfileResponse;
import com.smartlibrary.dto.MemberProfileDto.UpdateProfileRequest;
import com.smartlibrary.dto.MemberProfileDto.ChangePasswordRequest;
import com.smartlibrary.dto.MemberProfileDto.MessageResponse;

import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.MemberProfileService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class MemberProfileController {

    private final MemberProfileService memberProfileService;

    // ==========================================
    // GET PROFILE
    // ==========================================

    @GetMapping("/profile")
    public ProfileResponse getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {

        return memberProfileService.getProfile(
                principal.getId()
        );
    }

    // ==========================================
    // UPDATE PROFILE
    // ==========================================

    @PutMapping("/profile")
    public ProfileResponse updateProfile(

            @AuthenticationPrincipal UserPrincipal principal,

            @Valid @RequestBody UpdateProfileRequest request

    ) {

        return memberProfileService.updateProfile(
                principal.getId(),
                request
        );
    }

    // ==========================================
    // CHANGE PASSWORD
    // ==========================================

    @PutMapping("/change-password")
    public MessageResponse changePassword(

            @AuthenticationPrincipal UserPrincipal principal,

            @Valid @RequestBody ChangePasswordRequest request

    ) {

        return memberProfileService.changePassword(
                principal.getId(),
                request
        );
    }
}