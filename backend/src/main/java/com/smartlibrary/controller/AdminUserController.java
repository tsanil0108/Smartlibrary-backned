package com.smartlibrary.controller;

import com.smartlibrary.dto.MembershipDto.MembershipResponse;
import com.smartlibrary.dto.MembershipDto.UpdateMembershipRequest;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.dto.UserDto.UpdateStatusRequest;
import com.smartlibrary.dto.UserDto.UserResponse;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.MembershipService;
import com.smartlibrary.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final MembershipService membershipService;

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return userService.search(q, pageable);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}/status")                            // activate / suspend
    public UserResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request,
                                     @AuthenticationPrincipal UserPrincipal me) {
        return userService.changeStatus(id, request.status(), me.getId());
    }

    @PutMapping("/{id}/membership")
    public MembershipResponse updateMembership(@PathVariable Long id, @Valid @RequestBody UpdateMembershipRequest request,
                                               @AuthenticationPrincipal UserPrincipal me) {
        return membershipService.assign(id, request, me.getId());
    }

    @GetMapping("/{id}/memberships")
    public List<MembershipResponse> membershipHistory(@PathVariable Long id) {
        return membershipService.history(id);
    }
}
