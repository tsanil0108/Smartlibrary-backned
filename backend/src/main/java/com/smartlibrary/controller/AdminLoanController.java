package com.smartlibrary.controller;

import com.smartlibrary.dto.LoanDto.AdminIssueRequest;
import com.smartlibrary.dto.LoanDto.LoanResponse;
import com.smartlibrary.dto.PageResponse;
import com.smartlibrary.entity.LoanStatus;
import com.smartlibrary.security.UserPrincipal;
import com.smartlibrary.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/loans")
@RequiredArgsConstructor
public class AdminLoanController {

    private final LoanService loanService;

    @GetMapping
    public PageResponse<LoanResponse> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) Boolean overdue,
            @PageableDefault(size = 20, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.find(userId, status, overdue, pageable);
    }


    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse issue(@Valid @RequestBody AdminIssueRequest request, @AuthenticationPrincipal UserPrincipal me) {
        return loanService.borrow(request.userId(), request.bookId(), me.getId());
    }
}
