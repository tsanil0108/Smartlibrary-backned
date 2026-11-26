package com.smartlibrary.service;

import com.smartlibrary.config.PaymentProperties;

import com.smartlibrary.dto.PaymentDto.*;
import com.smartlibrary.dto.RazorpayDto.OrderResponse;

import com.smartlibrary.entity.*;

import com.smartlibrary.repository.*;

import com.smartlibrary.exception.BusinessRuleException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProperties properties;

    private final PaymentOrderRepository orders;

    private final DemoPaymentRepository payments;

    private final CouponRepository coupons;

    private final UserRepository users;

    private final RazorpayService razorpayService;

    private final Clock clock;

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
    // VALIDATE MEMBER
    // ==========================================

    private User validateMember(User user) {

        if (user.getRole() != Role.MEMBER

                || user.getStatus() != UserStatus.ACTIVE) {

            throw error(

                    "INVALID_MEMBER",

                    "Active member account required."
            );
        }

        return user;
    }

    private User getMember(Long userId) {

        return validateMember(

                users.findById(userId)

                        .orElseThrow(() -> error(

                                "MEMBER_NOT_FOUND",

                                "Member account not found."
                        ))
        );
    }

    private User lockMember(Long userId) {

        return validateMember(

                users.findByIdForUpdate(userId)

                        .orElseThrow(() -> error(

                                "MEMBER_NOT_FOUND",

                                "Member account not found."
                        ))
        );
    }

    // ==========================================
    // NORMALIZE OPTIONAL COUPON
    // ==========================================

    private String normalizeCoupon(String code) {

        if (code == null || code.isBlank()) {

            return null;
        }

        String normalized = code.trim()
                .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z0-9_-]{3,40}")) {

            throw error(

                    "COUPON_INVALID",

                    "Invalid coupon code format."
            );
        }

        return normalized;
    }

    // ==========================================
    // VALIDATE COUPON
    // ==========================================

    private void validateCoupon(
            Coupon coupon,
            Long userId
    ) {

        if (!properties.isCouponEnabled()) {

            throw error(

                    "COUPON_DISABLED",

                    "Coupon redemption is currently disabled."
            );
        }

        if (!coupon.isActive()) {

            throw error(

                    "COUPON_INACTIVE",

                    "This coupon is inactive."
            );
        }

        if (coupon.getExpiresOn() == null

                || coupon.getExpiresOn()
                .isBefore(LocalDate.now(clock))) {

            throw error(

                    "COUPON_EXPIRED",

                    "This coupon has expired."
            );
        }

        if (coupon.getPercent() < 1

                || coupon.getPercent() > 100) {

            throw error(

                    "COUPON_INVALID",

                    "Invalid coupon discount."
            );
        }

        if (payments.existsByUserIdAndCouponCode(

                userId,

                coupon.getCode()

        )) {

            throw error(

                    "COUPON_ALREADY_USED",

                    "You have already used this coupon."
            );
        }

        List<PaymentOrder> reservedOrders =

                orders.findByCouponCodeAndStatusIn(

                        coupon.getCode(),

                        RESERVED_STATUSES
                );

        boolean alreadyReserved = reservedOrders.stream()

                .anyMatch(order ->

                        userId.equals(order.getUserId())
                );

        if (alreadyReserved) {

            throw error(

                    "COUPON_RESERVED",

                    "You already have a pending order "
                            + "using this coupon."
            );
        }

        long reservedCount = reservedOrders.size();

        if ((long) coupon.getUsed() + reservedCount

                >= coupon.getMaxUses()) {

            throw error(

                    "COUPON_LIMIT_REACHED",

                    "Coupon usage limit has been reached."
            );
        }
    }

    // ==========================================
    // CALCULATE MEMBERSHIP PRICE
    // ==========================================

    private QuoteResponse calculateQuote(

            Long userId,

            String code,

            boolean lockCoupon

    ) {

        String normalized = normalizeCoupon(code);

        long originalPrice =
                properties.getMembershipPricePaise();

        int discountPercent = 0;

        if (normalized != null) {

            Coupon coupon;

            if (lockCoupon) {

                coupon = coupons.lock(normalized)

                        .orElseThrow(() -> error(

                                "COUPON_NOT_FOUND",

                                "Invalid coupon code."
                        ));

            } else {

                coupon = coupons.findById(normalized)

                        .orElseThrow(() -> error(

                                "COUPON_NOT_FOUND",

                                "Invalid coupon code."
                        ));
            }

            validateCoupon(coupon, userId);

            discountPercent = coupon.getPercent();
        }

        long discount = BigInteger

                .valueOf(originalPrice)

                .multiply(
                        BigInteger.valueOf(discountPercent)
                )

                .divide(
                        BigInteger.valueOf(100)
                )

                .longValueExact();

        long payable = originalPrice - discount;

        return new QuoteResponse(

                normalized,

                originalPrice,

                discount,

                payable,

                discountPercent,

                "INR",

                properties.getMembershipPlanName(),

                properties.getMembershipDays(),

                payable == 0,

                razorpayService.isConfigured()
        );
    }

    // ==========================================
    // PREVIEW MEMBERSHIP PRICE
    // ==========================================

    @Transactional(readOnly = true)
    public QuoteResponse quote(

            Long userId,

            String code

    ) {

        getMember(userId);

        return calculateQuote(

                userId,

                code,

                false
        );
    }

    // ==========================================
    // CREATE ONLINE PAYMENT ORDER
    // ==========================================

    @Transactional
    public CreateOrderResponse createOrder(

            Long userId,

            CreateOrderRequest request

    ) {

        User user = lockMember(userId);

        if (!razorpayService.isConfigured()) {

            throw error(

                    "ONLINE_PAYMENT_DISABLED",

                    "Online payment is currently unavailable."
            );
        }

        QuoteResponse quote = calculateQuote(

                userId,

                request.code(),

                true
        );

        if (quote.payablePaise() == 0) {

            throw error(

                    "FREE_REDEMPTION_REQUIRED",

                    "Use coupon redemption for a "
                            + "100% discount coupon."
            );
        }

        if (quote.payablePaise() < 100) {

            throw error(

                    "INVALID_PAYMENT_AMOUNT",

                    "Minimum online payment amount is ₹1."
            );
        }

        // Unique reference within Razorpay's
        // receipt length limit.

        String reference = "SLM-" +

                UUID.randomUUID()

                        .toString()

                        .replace("-", "");

        PaymentOrder order = new PaymentOrder();

        order.setUserId(userId);

        order.setOrderReference(reference);

        order.setCouponCode(quote.couponCode());

        order.setOriginalPaise(quote.originalPaise());

        order.setDiscountPaise(quote.discountPaise());

        order.setPayablePaise(quote.payablePaise());

        order.setCurrency("INR");

        order.setMembershipDays(quote.membershipDays());

        order.setPlanName(quote.planName());

        order.setStatus(PaymentOrderStatus.CREATED);

        order.setExpiresAt(

                Instant.now(clock).plus(

                        properties.getOrderExpiryMinutes(),

                        ChronoUnit.MINUTES
                )
        );

        orders.saveAndFlush(order);

        // Server-side Razorpay order creation.

        OrderResponse gatewayOrder =

                razorpayService.createOrder(

                        order.getPayablePaise(),

                        order.getOrderReference(),

                        userId
                );

        order.setRazorpayOrderId(gatewayOrder.id());

        order.setStatus(PaymentOrderStatus.PENDING);

        orders.saveAndFlush(order);

        return new CreateOrderResponse(

                order.getId(),

                order.getOrderReference(),

                order.getRazorpayOrderId(),

                razorpayService.getPublicKeyId(),

                order.getCouponCode(),

                order.getOriginalPaise(),

                order.getDiscountPaise(),

                order.getPayablePaise(),

                order.getCurrency(),

                order.getPlanName(),

                order.getMembershipDays(),

                user.getName(),

                user.getEmail(),

                order.getStatus().name()
        );
    }

    // ==========================================
    // MEMBER ORDER DETAILS
    // ==========================================

    @Transactional(readOnly = true)
    public CreateOrderResponse getOrder(

            Long userId,

            Long orderId

    ) {

        User user = getMember(userId);

        PaymentOrder order = orders

                .findByIdAndUserId(orderId, userId)

                .orElseThrow(() -> error(

                        "ORDER_NOT_FOUND",

                        "Payment order not found."
                ));

        String keyId = razorpayService.isConfigured()

                ? razorpayService.getPublicKeyId()

                : null;

        return new CreateOrderResponse(

                order.getId(),

                order.getOrderReference(),

                order.getRazorpayOrderId(),

                keyId,

                order.getCouponCode(),

                order.getOriginalPaise(),

                order.getDiscountPaise(),

                order.getPayablePaise(),

                order.getCurrency(),

                order.getPlanName(),

                order.getMembershipDays(),

                user.getName(),

                user.getEmail(),

                order.getStatus().name()
        );
    }
}