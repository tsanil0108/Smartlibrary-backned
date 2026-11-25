package com.smartlibrary.controller;

import com.smartlibrary.dto.FineDto.FineResponse;
import com.smartlibrary.dto.FineDto.FineSummaryResponse;
import com.smartlibrary.dto.FineDto.FineEstimateResponse;
import com.smartlibrary.dto.FineDto.WaiveFineRequest;

import com.smartlibrary.dto.LoanDto.ReturnCondition;

import com.smartlibrary.entity.FineStatus;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.FineService;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    // ==========================================
    // MEMBER: MY FINES
    // ==========================================

    @GetMapping("/member/fines")
    @PreAuthorize("hasRole('MEMBER')")
    public List<FineResponse> getMyFines(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.getMyFines(
                principal.getId()
        );
    }

    // ==========================================
    // MEMBER: FINE SUMMARY
    // ==========================================

    @GetMapping("/member/fines/summary")
    @PreAuthorize("hasRole('MEMBER')")
    public FineSummaryResponse getMySummary(

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.getMySummary(
                principal.getId()
        );
    }

    // ==========================================
    // MEMBER: CURRENT OVERDUE ESTIMATE
    // ==========================================

    @GetMapping(
            "/member/fines/estimate/{loanId}"
    )
    @PreAuthorize("hasRole('MEMBER')")
    public FineEstimateResponse estimateFine(

            @PathVariable Long loanId,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.estimateFine(

                loanId,

                principal.getId(),

                false,

                ReturnCondition.GOOD
        );
    }

    // ==========================================
    // ADMIN: GET ALL FINES
    // ==========================================

    @GetMapping("/admin/fines")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FineResponse> getAllFines(

            @RequestParam(required = false)
            FineStatus status

    ) {

        return fineService.getAllFines(status);
    }

    // ==========================================
    // ADMIN: FINE SUMMARY
    // ==========================================

    @GetMapping("/admin/fines/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public FineSummaryResponse getAdminSummary() {

        return fineService.getAdminSummary();
    }

    // ==========================================
    // ADMIN: RECORD CASH PAYMENT
    // ==========================================

    @PatchMapping("/admin/fines/{fineId}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public FineResponse markPaid(

            @PathVariable Long fineId,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.markPaid(

                fineId,

                principal.getId()
        );
    }

    // ==========================================
    // ADMIN: WAIVE FINE
    // ==========================================

    @PatchMapping("/admin/fines/{fineId}/waive")
    @PreAuthorize("hasRole('ADMIN')")
    public FineResponse waiveFine(

            @PathVariable Long fineId,

            @Valid
            @RequestBody
            WaiveFineRequest request,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.waiveFine(

                fineId,

                principal.getId(),

                request.reason()
        );
    }

    // ==========================================
    // ADMIN: ESTIMATE RETURN CHARGES
    // ==========================================

    @GetMapping(
            "/admin/fines/estimate/{loanId}"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public FineEstimateResponse adminEstimate(

            @PathVariable Long loanId,

            @RequestParam(defaultValue = "GOOD")
            ReturnCondition condition,

            @AuthenticationPrincipal
            UserPrincipal principal

    ) {

        return fineService.estimateFine(

                loanId,

                principal.getId(),

                true,

                condition
        );
    }
}