package com.smartlibrary.controller;

import com.smartlibrary.dto.LibraryCardDto.CardResponse;
import com.smartlibrary.dto.LibraryCardDto.VerificationResponse;
import com.smartlibrary.dto.LibraryCardDto.VerifyCardRequest;
import com.smartlibrary.dto.LibraryCardDto.UpdateCardStatusRequest;

import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.LibraryCardService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LibraryCardController {

    private final LibraryCardService cardService;

    // ==========================================
    // MEMBER: GET DIGITAL LIBRARY CARD
    // ==========================================

    @GetMapping("/member/library-card")
    @PreAuthorize("hasRole('MEMBER')")
    public CardResponse getMyCard(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return cardService.getMyCard(
                principal.getId()
        );
    }

    // ==========================================
    // MEMBER: REGENERATE QR CODE
    // ==========================================

    @PostMapping("/member/library-card/regenerate")
    @PreAuthorize("hasRole('MEMBER')")
    public CardResponse regenerateQr(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return cardService.regenerateQr(
                principal.getId()
        );
    }

    // ==========================================
    // ADMIN: VERIFY SCANNED QR CODE
    // ==========================================

    @PostMapping("/admin/library-cards/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse verifyQr(

            @Valid
            @RequestBody
            VerifyCardRequest request

    ) {

        return cardService.verifyQr(
                request.qrCode()
        );
    }

    // ==========================================
    // ADMIN: VERIFY USING CARD NUMBER
    // ==========================================

    @GetMapping(
            "/admin/library-cards/verify/{cardNumber}"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse verifyCardNumber(

            @PathVariable String cardNumber

    ) {

        return cardService.verifyCardNumber(
                cardNumber
        );
    }

    // ==========================================
    // ADMIN: ENABLE OR DISABLE LIBRARY CARD
    // ==========================================

    @PatchMapping(
            "/admin/library-cards/{userId}/status"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse updateStatus(

            @PathVariable Long userId,

            @AuthenticationPrincipal
            UserPrincipal principal,

            @RequestBody
            UpdateCardStatusRequest request

    ) {

        return cardService.updateCardStatus(

                userId,

                principal.getId(),

                request.active()
        );
    }
}