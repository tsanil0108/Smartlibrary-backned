package com.smartlibrary.service;

import com.smartlibrary.config.PaymentProperties;

import com.smartlibrary.dto.PaymentDto.*;
import com.smartlibrary.dto.MembershipDto.UpdateMembershipRequest;
import com.smartlibrary.dto.RazorpayDto.PaymentResponse;

import com.smartlibrary.entity.*;

import com.smartlibrary.repository.*;

import com.smartlibrary.exception.BusinessRuleException;

import com.smartlibrary.event.PaymentConfirmationEvent;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final PaymentProperties properties;

    private final PaymentOrderRepository orders;

    private final DemoPaymentRepository payments;

    private final CouponRepository coupons;

    private final UserRepository users;

    private final MembershipRepository memberships;

    private final MembershipService membershipService;

    private final RazorpayService razorpayService;

    private final PaymentService paymentService;

    private final Clock clock;

    private final ApplicationEventPublisher eventPublisher;

    private final EntityManager entityManager;

    private static final List<PaymentOrderStatus>
            RESERVED_STATUSES = List.of(

            PaymentOrderStatus.CREATED,

            PaymentOrderStatus.PENDING,

            PaymentOrderStatus.CAPTURED
    );

    // ==========================================
    // BUSINESS ERROR
    // ==========================================

    private BusinessRuleException error(

            String code,

            String message

    ) {

        return new BusinessRuleException(
                code,
                message
        );
    }

    // ==========================================
    // LOCK MEMBER
    // ==========================================

    private User lockMember(
            Long userId
    ) {

        User user = users.findByIdForUpdate(userId)

                .orElseThrow(() -> error(

                        "MEMBER_NOT_FOUND",

                        "Member account not found."
                ));

        if (user.getRole() != Role.MEMBER) {

            throw error(

                    "INVALID_MEMBER",

                    "Member account required."
            );
        }

        return user;
    }

    private void requireActiveMember(
            User user
    ) {

        if (user.getStatus() != UserStatus.ACTIVE) {

            throw error(

                    "MEMBER_INACTIVE",

                    "Member account is inactive."
            );
        }
    }

    // ==========================================
    // VERIFY ONLINE PAYMENT
    // ==========================================

    @Transactional
    public PaymentResult verify(

            Long userId,

            VerifyRequest request

    ) {

        PaymentOrder snapshot = orders

                .findByIdAndUserId(

                        request.orderId(),

                        userId
                )

                .orElseThrow(() -> error(

                        "ORDER_NOT_FOUND",

                        "Payment order not found."
                ));

        if (snapshot.getRazorpayOrderId() == null

                || !snapshot.getRazorpayOrderId().equals(
                request.razorpayOrderId()
        )) {

            throw error(

                    "INVALID_ORDER",

                    "Payment order does not match."
            );
        }

        boolean signatureValid =
                razorpayService.verifyPaymentSignature(

                        snapshot.getRazorpayOrderId(),

                        request.razorpayPaymentId(),

                        request.razorpaySignature()
                );

        if (!signatureValid) {

            throw error(

                    "INVALID_SIGNATURE",

                    "Payment signature verification failed."
            );
        }

        return completeOnlinePayment(

                snapshot.getRazorpayOrderId(),

                request.razorpayPaymentId()
        );
    }

    // ==========================================
    // WEBHOOK AND SCHEDULER PAYMENT COMPLETION
    // ==========================================

    @Transactional
    public PaymentResult completeFromWebhook(

            String razorpayOrderId,

            String razorpayPaymentId

    ) {

        return completeOnlinePayment(

                razorpayOrderId,

                razorpayPaymentId
        );
    }

    // ==========================================
    // COMPLETE VERIFIED ONLINE PAYMENT
    // ==========================================

    private PaymentResult completeOnlinePayment(

            String razorpayOrderId,

            String razorpayPaymentId

    ) {

        PaymentOrder snapshot = orders

                .findByRazorpayOrderId(
                        razorpayOrderId
                )

                .orElseThrow(() -> error(

                        "ORDER_NOT_FOUND",

                        "Payment order not found."
                ));

        // Lock member first to maintain consistent
        // database locking order.

        User user = lockMember(
                snapshot.getUserId()
        );

        PaymentOrder order = orders

                .findByIdForUpdate(
                        snapshot.getId()
                )

                .orElseThrow(() -> error(

                        "ORDER_NOT_FOUND",

                        "Payment order not found."
                ));

        // Refresh after obtaining the lock so that
        // concurrent completion is visible.

        entityManager.refresh(order);

        // ======================================
        // DUPLICATE PAYMENT PROTECTION
        // ======================================

        if (order.isCompleted()) {

            if (!razorpayPaymentId.equals(
                    order.getRazorpayPaymentId()
            )) {

                throw error(

                        "PAYMENT_MISMATCH",

                        "Order was completed with another payment."
                );
            }

            DemoPayment existing = payments

                    .findByPaymentOrderId(
                            order.getId()
                    )

                    .orElseThrow(() -> error(

                            "PAYMENT_NOT_FOUND",

                            "Completed payment record not found."
                    ));

            return result(existing, order);
        }

        // ======================================
        // VALIDATE ORDER STATUS
        // ======================================

        if (order.getStatus() != PaymentOrderStatus.PENDING

                && order.getStatus() != PaymentOrderStatus.CAPTURED) {

            throw error(

                    "ORDER_NOT_PAYABLE",

                    "Order requires manual payment reconciliation."
            );
        }

        requireActiveMember(user);

        // ======================================
        // VERIFY ACTUAL RAZORPAY PAYMENT
        // ======================================

        PaymentResponse gatewayPayment =
                razorpayService.verifyCapturedPayment(

                        order.getRazorpayOrderId(),

                        razorpayPaymentId,

                        order.getPayablePaise()
                );

        if (payments.existsByRazorpayPaymentId(
                razorpayPaymentId
        )) {

            throw error(

                    "PAYMENT_ALREADY_USED",

                    "Payment has already been processed."
            );
        }

        // ======================================
        // CONSUME RESERVED COUPON
        // ======================================

        if (order.getCouponCode() != null) {

            consumeReservedCoupon(order);
        }

        // ======================================
        // UPDATE VERIFIED PAYMENT ORDER
        // ======================================

        order.setRazorpayPaymentId(
                gatewayPayment.id()
        );

        order.setPaymentMethod(

                gatewayPayment.method() == null

                        ? "RAZORPAY"

                        : gatewayPayment.method()
                        .toUpperCase(Locale.ROOT)
        );

        order.setCapturedAt(
                Instant.now(clock)
        );

        order.setVerifiedAt(
                Instant.now(clock)
        );

        order.setStatus(
                PaymentOrderStatus.CAPTURED
        );

        return finishMembership(
                user,
                order
        );
    }

    // ==========================================
    // CONSUME RESERVED COUPON
    // ==========================================

    private void consumeReservedCoupon(
            PaymentOrder order
    ) {

        Coupon coupon = coupons

                .lock(
                        order.getCouponCode()
                )

                .orElseThrow(() -> error(

                        "COUPON_NOT_FOUND",

                        "Reserved coupon not found."
                ));

        if (payments.existsByUserIdAndCouponCode(

                order.getUserId(),

                coupon.getCode()

        )) {

            throw error(

                    "COUPON_ALREADY_USED",

                    "Coupon has already been redeemed."
            );
        }

        if (coupon.getUsed() >= coupon.getMaxUses()) {

            throw error(

                    "COUPON_LIMIT_REACHED",

                    "Coupon usage limit reached. "
                            + "Payment requires reconciliation."
            );
        }

        /*
         * Honor the coupon reserved at order creation.
         *
         * The coupon may expire or become inactive
         * after the payment order was created.
         */

        coupon.setUsed(
                coupon.getUsed() + 1
        );

        coupons.save(coupon);
    }

    // ==========================================
    // REDEEM 100% DISCOUNT COUPON
    // ==========================================

    @Transactional
    public PaymentResult redeem(

            Long userId,

            RedeemRequest request

    ) {

        User user = lockMember(userId);

        requireActiveMember(user);

        if (!properties.isCouponEnabled()) {

            throw error(

                    "COUPON_DISABLED",

                    "Coupon redemption is disabled."
            );
        }

        if (request.code() == null
                || request.code().isBlank()) {

            throw error(

                    "COUPON_REQUIRED",

                    "Coupon code is required."
            );
        }

        String code = request.code()

                .trim()

                .toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z0-9_-]{3,40}")) {

            throw error(

                    "COUPON_INVALID",

                    "Invalid coupon code format."
            );
        }

        // ======================================
        // PREVENT DUPLICATE COUPON REDEMPTION
        // ======================================

        DemoPayment previous = payments

                .findByUserIdAndCouponCode(
                        userId,
                        code
                )

                .orElse(null);

        if (previous != null) {

            if (previous.isFreeRedemption()

                    && previous.isSuccessfulPayment()) {

                return new PaymentResult(

                        previous.getId(),

                        previous.getPaymentOrderId(),

                        previous.getMembershipId(),

                        previous.getTransactionId(),

                        previous.getStatus(),

                        previous.getPaymentMethod(),

                        previous.getPaidPaise(),

                        "Coupon has already been redeemed."
                );
            }

            throw error(

                    "COUPON_ALREADY_USED",

                    "Coupon has already been used."
            );
        }

        // ======================================
        // LOCK AND VALIDATE COUPON
        // ======================================

        Coupon coupon = coupons

                .lock(code)

                .orElseThrow(() -> error(

                        "COUPON_NOT_FOUND",

                        "Invalid coupon code."
                ));

        QuoteResponse quote =
                paymentService.quote(

                        userId,

                        code
                );

        if (!quote.freeRedemption()

                || quote.payablePaise() != 0) {

            throw error(

                    "PAYMENT_REQUIRED",

                    "This coupon requires online payment."
            );
        }

        // ======================================
        // CHECK RESERVED COUPON USAGE
        // ======================================

        long reservedCount = orders

                .findByCouponCodeAndStatusIn(

                        code,

                        RESERVED_STATUSES
                )

                .size();

        if ((long) coupon.getUsed() + reservedCount

                >= coupon.getMaxUses()) {

            throw error(

                    "COUPON_LIMIT_REACHED",

                    "Coupon usage limit reached."
            );
        }

        // ======================================
        // CREATE FREE PAYMENT ORDER
        // ======================================

        PaymentOrder order = new PaymentOrder();

        order.setUserId(userId);

        order.setOrderReference(

                "SLM-" + UUID.randomUUID()

                        .toString()

                        .replace("-", "")
        );

        order.setCouponCode(code);

        order.setOriginalPaise(
                quote.originalPaise()
        );

        order.setDiscountPaise(
                quote.discountPaise()
        );

        order.setPayablePaise(0);

        order.setCurrency("INR");

        order.setPlanName(
                quote.planName()
        );

        order.setMembershipDays(
                quote.membershipDays()
        );

        order.setPaymentMethod("COUPON");

        order.setStatus(
                PaymentOrderStatus.CREATED
        );

        order.setVerifiedAt(
                Instant.now(clock)
        );

        orders.saveAndFlush(order);

        // ======================================
        // INCREASE COUPON USAGE
        // ======================================

        coupon.setUsed(
                coupon.getUsed() + 1
        );

        coupons.save(coupon);

        // ======================================
        // ACTIVATE MEMBERSHIP
        // ======================================

        return finishMembership(
                user,
                order
        );
    }

    // ==========================================
    // MEMBERSHIP ACTIVATION AND RENEWAL
    // ==========================================

    private PaymentResult finishMembership(

            User user,

            PaymentOrder order

    ) {

        LocalDate today =
                LocalDate.now(clock);

        List<Membership> active =
                memberships.findActiveOn(

                        user.getId(),

                        today
                );

        LocalDate startDate = today;

        // Membership end dates are inclusive.

        LocalDate endDate = today.plusDays(

                order.getMembershipDays() - 1L
        );

        // ======================================
        // PRESERVE EXISTING MEMBERSHIP VALIDITY
        // ======================================

        for (Membership current : active) {

            LocalDate extendedEndDate =
                    current.getEndDate().plusDays(

                            order.getMembershipDays()
                    );

            if (extendedEndDate.isAfter(endDate)) {

                endDate = extendedEndDate;
            }
        }

        // ======================================
        // ASSIGN PREMIUM MEMBERSHIP
        // ======================================

        var membership = membershipService.assign(

                user.getId(),

                new UpdateMembershipRequest(

                        order.getPlanName(),

                        startDate,

                        endDate,

                        MembershipStatus.ACTIVE
                ),

                user.getId()
        );

        // ======================================
        // CREATE PAYMENT HISTORY RECORD
        // ======================================

        DemoPayment payment = new DemoPayment();

        payment.setUserId(
                user.getId()
        );

        payment.setCouponCode(
                order.getCouponCode()
        );

        payment.setOriginalPaise(
                order.getOriginalPaise()
        );

        payment.setDiscountPaise(
                order.getDiscountPaise()
        );

        payment.setPaidPaise(
                order.getPayablePaise()
        );

        payment.setMembershipId(
                membership.id()
        );

        payment.setPaymentOrderId(
                order.getId()
        );

        payment.setRazorpayOrderId(
                order.getRazorpayOrderId()
        );

        payment.setRazorpayPaymentId(
                order.getRazorpayPaymentId()
        );

        payment.setStoredPaymentMethod(
                order.getPaymentMethod()
        );

        payment.setStoredStatus(
                "SUCCESS"
        );

        payment.setCurrency(
                order.getCurrency()
        );

        payment.setCreatedAt(
                Instant.now(clock)
        );

        payment.setVerifiedAt(
                Instant.now(clock)
        );

        payments.saveAndFlush(payment);

        // ======================================
        // MARK PAYMENT ORDER AS SUCCESS
        // ======================================

        order.setMembershipId(
                membership.id()
        );

        order.setCompletedPaymentId(
                payment.getId()
        );

        order.setCompletedAt(
                Instant.now(clock)
        );

        order.setStatus(
                PaymentOrderStatus.SUCCESS
        );

        orders.saveAndFlush(order);

        // ======================================
        // PHASE 6 - PAYMENT CONFIRMATION EMAIL
        // ======================================

        /*
         * PaymentConfirmationListener handles this
         * event after the database transaction commits.
         *
         * Email failure must not rollback a
         * successful payment or membership.
         */

        if (properties.isConfirmationEmailEnabled()) {

            eventPublisher.publishEvent(

                    new PaymentConfirmationEvent(

                            payment.getId(),

                            user.getName(),

                            user.getEmail(),

                            order.getPlanName(),

                            payment.getTransactionId(),

                            payment.getPaymentMethod(),

                            payment.getOriginalPaise(),

                            payment.getDiscountPaise(),

                            payment.getPaidPaise(),

                            startDate,

                            endDate
                    )
            );
        }

        // ======================================
        // RETURN SUCCESS RESPONSE
        // ======================================

        return result(
                payment,
                order
        );
    }

    // ==========================================
    // FINAL PAYMENT RESPONSE
    // ==========================================

    private PaymentResult result(

            DemoPayment payment,

            PaymentOrder order

    ) {

        return new PaymentResult(

                payment.getId(),

                order.getId(),

                payment.getMembershipId(),

                payment.getTransactionId(),

                payment.getStatus(),

                payment.getPaymentMethod(),

                payment.getPaidPaise(),

                "Membership activated successfully."
        );
    }
}