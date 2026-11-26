package com.smartlibrary.controller;

import com.smartlibrary.dto.PaymentDto.*;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.PaymentService;
import com.smartlibrary.service.PaymentCompletionService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
public class PaymentController {

    private final PaymentService paymentService;

    private final PaymentCompletionService completionService;

    // ==========================================
    // CHECKOUT QUOTE
    // ==========================================

    @PostMapping("/checkout/quote")
    public QuoteResponse quote(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @Valid
            @RequestBody
            QuoteRequest request

    ) {

        return paymentService.quote(

                principal.getId(),

                request.code()
        );
    }

    // ==========================================
    // CREATE ONLINE PAYMENT ORDER
    // ==========================================

    @PostMapping("/create-order")
    public CreateOrderResponse createOrder(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @Valid
            @RequestBody
            CreateOrderRequest request

    ) {

        return paymentService.createOrder(

                principal.getId(),

                request
        );
    }

    // ==========================================
    // GET PAYMENT ORDER
    // ==========================================

    @GetMapping("/orders/{orderId}")
    public CreateOrderResponse getOrder(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @PathVariable Long orderId

    ) {

        return paymentService.getOrder(

                principal.getId(),

                orderId
        );
    }

    // ==========================================
    // VERIFY RAZORPAY PAYMENT
    // ==========================================

    @PostMapping("/verify")
    public PaymentResult verify(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @Valid
            @RequestBody
            VerifyRequest request

    ) {

        return completionService.verify(

                principal.getId(),

                request
        );
    }

    // ==========================================
    // REDEEM 100% COUPON
    // ==========================================

    @PostMapping("/redeem")
    public PaymentResult redeem(

            @AuthenticationPrincipal
            UserPrincipal principal,

            @Valid
            @RequestBody
            RedeemRequest request

    ) {

        return completionService.redeem(

                principal.getId(),

                request
        );
    }
}