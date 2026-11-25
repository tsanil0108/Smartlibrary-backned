package com.smartlibrary.controller;

import com.smartlibrary.dto.WishlistDto.WishlistResponse;
import com.smartlibrary.dto.WishlistDto.WishlistStatusResponse;
import com.smartlibrary.dto.WishlistDto.WishlistCountResponse;
import com.smartlibrary.dto.WishlistDto.MessageResponse;

import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.WishlistService;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/member/wishlist")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class WishlistController {

    private final WishlistService wishlistService;

    // ==========================================
    // GET MY WISHLIST
    // ==========================================

    @GetMapping
    public List<WishlistResponse> getWishlist(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return wishlistService.getMyWishlist(
                principal.getId()
        );
    }

    // ==========================================
    // ADD BOOK
    // ==========================================

    @PostMapping("/{bookId}")
    public WishlistResponse add(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @PathVariable Long bookId

    ) {

        return wishlistService.add(
                principal.getId(),
                bookId
        );
    }

    // ==========================================
    // REMOVE BOOK
    // ==========================================

    @DeleteMapping("/{bookId}")
    public MessageResponse remove(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @PathVariable Long bookId

    ) {

        return wishlistService.remove(
                principal.getId(),
                bookId
        );
    }

    // ==========================================
    // CHECK FAVOURITE STATUS
    // ==========================================

    @GetMapping("/{bookId}/status")
    public WishlistStatusResponse check(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @PathVariable Long bookId

    ) {

        return wishlistService.check(
                principal.getId(),
                bookId
        );
    }

    // ==========================================
    // TOTAL WISHLIST COUNT
    // ==========================================

    @GetMapping("/count")
    public WishlistCountResponse count(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return wishlistService.count(
                principal.getId()
        );
    }
}