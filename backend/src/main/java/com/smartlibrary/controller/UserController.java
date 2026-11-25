package com.smartlibrary.controller;

import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.MembershipDto.MembershipResponse;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.UserDto.UserResponse;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.LoanService;
import com.smartlibrary.service.MembershipService;
import com.smartlibrary.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoanService loanService;
    private final MembershipService membershipService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal me) {
        return userService.get(me.getId());
    }


    @GetMapping("/me/loans")
    public PageResponse<LoanResponse> myLoans(
            @RequestParam(required = false) LoanStatus status,
            @AuthenticationPrincipal UserPrincipal me,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.find(me.getId(), status, null, pageable);
    }

    @GetMapping("/me/membership")
    public MembershipResponse myMembership(@AuthenticationPrincipal UserPrincipal me) {
        return membershipService.currentFor(me.getId());
    }

    @GetMapping("/{id}/reading-history")                     // ADMIN or the user himself
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public PageResponse<LoanResponse> readingHistory(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.find(id, null, null, pageable);
    }
}
