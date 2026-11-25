package com.smartlibrary.controller;

import com.smartlibrary.dto.CouponDto.*;
import com.smartlibrary.service.CouponService;
import com.smartlibrary.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService service;

    @GetMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public Object list() {

        return service.list();
    }

    @PostMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public Object create(
            @Valid @RequestBody Create request) {

        return service.create(request);
    }

    @PatchMapping("/admin/coupons/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public Object toggle(
            @PathVariable String code,
            @RequestParam boolean active) {

        return service.toggle(code, active);
    }

    @PostMapping("/payments/quote")
    @PreAuthorize("hasRole('MEMBER')")
    public Object quote(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody Checkout request) {

        return service.quote(
                me.getId(),
                request.code()
        );
    }

    @PostMapping("/payments/demo")
    @PreAuthorize("hasRole('MEMBER')")
    public Object checkout(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody Checkout request) {

        return service.checkout(
                me.getId(),
                request.code()
        );
    }

    @GetMapping("/payments/me")
    @PreAuthorize("hasRole('MEMBER')")
    public Object history(
            @AuthenticationPrincipal UserPrincipal me) {

        return service.history(me.getId());
    }
}