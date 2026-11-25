package com.smartlibrary.controller;

import com.smartlibrary.dto.LoanDto.BorrowRequest;
import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.LoanDto.ReturnRequest;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;


    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@Valid @RequestBody BorrowRequest request, @AuthenticationPrincipal UserPrincipal me) {
        return loanService.borrow(me.getId(), request.bookId(), null);
    }

    @PostMapping("/{id}/return")                            // MEMBER (own loan) or ADMIN
    public LoanResponse returnLoan(@PathVariable Long id,
                                   @RequestBody(required = false) ReturnRequest body,
                                   @AuthenticationPrincipal UserPrincipal me) {
        return loanService.returnLoan(id, me, body == null ? null : body.condition());
    }

    @PostMapping("/{id}/renew")
    public LoanResponse renew(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal me) {
        return loanService.renew(id, me);
    }
}
