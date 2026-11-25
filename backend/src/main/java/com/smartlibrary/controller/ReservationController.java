package com.smartlibrary.controller;

import com.smartlibrary.dto.ReservationDto.ReservationResponse;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.ReservationService;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // MEMBER: Reserve book

    @PostMapping("/member/reservations/{bookId}")
    @PreAuthorize("hasRole('MEMBER')")
    public ReservationResponse reserve(

            @PathVariable Long bookId,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return reservationService.reserve(
                principal.getId(),
                bookId
        );
    }

    // MEMBER: My reservations

    @GetMapping("/member/reservations")
    @PreAuthorize("hasRole('MEMBER')")
    public List<ReservationResponse> myReservations(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return reservationService.getMyReservations(
                principal.getId()
        );
    }

    // MEMBER: Cancel reservation

    @DeleteMapping("/member/reservations/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ReservationResponse cancel(

            @PathVariable Long id,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return reservationService.cancel(
                id,
                principal.getId(),
                false
        );
    }

    // ADMIN: All reservations

    @GetMapping("/admin/reservations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReservationResponse> allReservations() {

        return reservationService.getAllReservations();
    }

    // ADMIN: Cancel reservation

    @DeleteMapping("/admin/reservations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservationResponse adminCancel(

            @PathVariable Long id,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return reservationService.cancel(
                id,
                principal.getId(),
                true
        );
    }
}