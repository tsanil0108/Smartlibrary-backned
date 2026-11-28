package com.smartlibrary.controller;

import com.smartlibrary.dto.LoanDto.BorrowRequest;
import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.LoanDto.ReturnRequest;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.LoanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    // ==========================================
    // GET LOGGED-IN MEMBER LOANS
    // ==========================================

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public PageResponse<LoanResponse> getMyLoans(

            @AuthenticationPrincipal UserPrincipal me,

            @PageableDefault(
                    size = 20,
                    sort = "id",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {

        return loanService.find(
                me.getId(),
                null,
                null,
                pageable
        );
    }

    // ==========================================
    // BORROW BOOK
    // ==========================================

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(

            @Valid @RequestBody BorrowRequest request,

            @AuthenticationPrincipal UserPrincipal me
    ) {

        return loanService.borrow(
                me.getId(),
                request.bookId(),
                null
        );
    }

    // ==========================================
    // RETURN BOOK
    // ==========================================

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(

            @PathVariable Long id,

            @RequestBody(required = false)
            ReturnRequest body,

            @AuthenticationPrincipal UserPrincipal me
    ) {

        return loanService.returnLoan(
                id,
                me,
                body == null ? null : body.condition()
        );
    }

    // ==========================================
    // RENEW BOOK
    // ==========================================

    @PostMapping("/{id}/renew")
    public LoanResponse renew(

            @PathVariable Long id,

            @AuthenticationPrincipal UserPrincipal me
    ) {

        return loanService.renew(id, me);
    }
}