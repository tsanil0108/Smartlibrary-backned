package com.smartlibrary.service;

import com.smartlibrary.config.PaymentProperties;

import com.smartlibrary.entity.*;
import com.smartlibrary.repository.*;

import com.smartlibrary.dto.CouponDto.*;
import com.smartlibrary.dto.MembershipDto.UpdateMembershipRequest;

import com.smartlibrary.event.PaymentCompletedEvent;

import com.smartlibrary.exception.BusinessRuleException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository coupons;

    private final DemoPaymentRepository payments;

    private final UserRepository users;

    private final MembershipRepository memberships;

    private final MembershipService membershipService;

    private final PaymentProperties paymentProperties;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    // ==========================================
    // NORMALIZE COUPON CODE
    // ==========================================

    private String normalize(String code) {

        if (code == null || code.isBlank()) {

            throw error("Coupon code is required");
        }

        String normalized = code.trim()
                .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z0-9_-]{3,40}")) {

            throw error("Invalid coupon code format");
        }

        return normalized;
    }

    // ==========================================
    // BUSINESS RULE ERROR
    // ==========================================

    private BusinessRuleException error(String message) {

        return new BusinessRuleException(

                "COUPON_INVALID",

                message
        );
    }

    // ==========================================
    // CHECK DEMO PAYMENT CONFIGURATION
    // ==========================================

    private void requireDemoEnabled() {

        if (!paymentProperties.isDemoEnabled()) {

            throw new BusinessRuleException(

                    "DEMO_PAYMENT_DISABLED",

                    "Demo membership checkout is disabled"
            );
        }
    }

    // ==========================================
    // VALIDATE MEMBER
    // ==========================================

    private void validateMember(User user) {

        if (user.getRole() != Role.MEMBER

                || user.getStatus() != UserStatus.ACTIVE) {

            throw new BusinessRuleException(

                    "INVALID_MEMBER",

                    "Active member required"
            );
        }
    }

    // ==========================================
    // ADMIN CREATE COUPON
    // ==========================================

    @Transactional
    public Coupon create(Create req) {

        if (req == null) {

            throw error("Coupon details are required");
        }

        String code = normalize(req.code());

        if (coupons.existsById(code)) {

            throw error("Coupon already exists");
        }

        if (req.percent() < 1 || req.percent() > 100) {

            throw error(
                    "Discount must be between 1 and 100 percent"
            );
        }

        if (req.maxUses() < 1) {

            throw error(
                    "Maximum uses must be greater than zero"
            );
        }

        LocalDate today = LocalDate.now(clock);

        if (req.expiresOn() == null

                || req.expiresOn().isBefore(today)) {

            throw error("Invalid expiry date");
        }

        Coupon coupon = new Coupon();

        coupon.setCode(code);

        coupon.setPercent(req.percent());

        coupon.setMaxUses(req.maxUses());

        coupon.setUsed(0);

        coupon.setExpiresOn(req.expiresOn());

        coupon.setActive(true);

        // Persist as a new entity rather than merging an
        // assigned ID into a potentially existing coupon.

        entityManager.persist(coupon);

        entityManager.flush();

        return coupon;
    }

    // ==========================================
    // ADMIN ACTIVATE / DEACTIVATE COUPON
    // ==========================================

    @Transactional
    public Coupon toggle(
            String code,
            boolean active
    ) {

        Coupon coupon = coupons.lock(
                        normalize(code)
                )
                .orElseThrow(
                        () -> error("Unknown coupon")
                );

        if (active) {

            if (coupon.getExpiresOn()
                    .isBefore(LocalDate.now(clock))) {

                throw error(
                        "Expired coupon cannot be activated"
                );
            }

            if (coupon.getUsed() >= coupon.getMaxUses()) {

                throw error(
                        "Coupon usage limit has been reached"
                );
            }
        }

        coupon.setActive(active);

        return coupons.save(coupon);
    }

    // ==========================================
    // ADMIN GET ALL COUPONS
    // ==========================================

    @Transactional(readOnly = true)
    public List<Coupon> list() {

        return coupons.findAll();
    }

    // ==========================================
    // MEMBER PAYMENT HISTORY
    // ==========================================

    @Transactional(readOnly = true)
    public List<DemoPayment> history(Long userId) {

        return payments.findByUserIdOrderByIdDesc(
                userId
        );
    }

    // ==========================================
    // VALIDATE COUPON
    // ==========================================

    private Quote validate(
            Coupon coupon,
            Long userId
    ) {

        if (!coupon.isActive()) {

            throw error("Coupon is inactive");
        }

        if (coupon.getExpiresOn() == null

                || coupon.getExpiresOn()
                .isBefore(LocalDate.now(clock))) {

            throw error("Coupon has expired");
        }

        if (coupon.getPercent() < 1

                || coupon.getPercent() > 100) {

            throw error("Invalid coupon discount");
        }

        if (coupon.getUsed() >= coupon.getMaxUses()) {

            throw error("Coupon usage limit reached");
        }

        if (payments.existsByUserIdAndCouponCode(

                userId,

                coupon.getCode()

        )) {

            throw error("Coupon already used");
        }

        long price =
                paymentProperties.getMembershipPricePaise();

        // Calculate discount on the backend.
        // Avoid multiplication overflow for large prices.

        long discount = java.math.BigInteger
                .valueOf(price)
                .multiply(
                        java.math.BigInteger.valueOf(
                                coupon.getPercent()
                        )
                )
                .divide(
                        java.math.BigInteger.valueOf(100)
                )
                .longValueExact();

        long payable = price - discount;

        return new Quote(

                coupon.getCode(),

                price,

                discount,

                payable,

                true
        );
    }

    // ==========================================
    // PREVIEW COUPON DISCOUNT
    // ==========================================

    @Transactional(readOnly = true)
    public Quote quote(
            Long userId,
            String code
    ) {

        requireDemoEnabled();

        User user = users.findById(userId)
                .orElseThrow(
                        () -> error("Unknown member")
                );

        validateMember(user);

        Coupon coupon = coupons.findById(
                        normalize(code)
                )
                .orElseThrow(
                        () -> error("Unknown coupon")
                );

        return validate(coupon, userId);
    }

    // ==========================================
    // DEMO MEMBERSHIP CHECKOUT
    // ==========================================

    @Transactional
    public DemoPayment checkout(
            Long userId,
            String code
    ) {

        requireDemoEnabled();

        String normalizedCode = normalize(code);

        // Lock user to prevent concurrent membership
        // activation for the same member.

        User user = users.findByIdForUpdate(userId)
                .orElseThrow(
                        () -> error("Unknown member")
                );

        validateMember(user);

        // ======================================
        // DUPLICATE REQUEST PROTECTION
        // ======================================

        Optional<DemoPayment> existingPayment =
                payments.findByUserIdAndCouponCode(

                        userId,

                        normalizedCode
                );

        if (existingPayment.isPresent()) {

            DemoPayment existing = existingPayment.get();

            if (!existing.isFreeRedemption()) {

                throw error(
                        "Existing transaction requires verification"
                );
            }

            // Return original successful transaction.
            // Do not extend membership again.
            // Do not increase coupon usage again.

            return existing;
        }

        // ======================================
        // LOCK COUPON
        // ======================================

        Coupon coupon = coupons.lock(normalizedCode)
                .orElseThrow(
                        () -> error("Unknown coupon")
                );

        Quote quote = validate(coupon, userId);

        // ======================================
        // ONLY ACCEPT 100% DISCOUNT
        // ======================================

        if (coupon.getPercent() != 100

                || quote.payablePaise() != 0) {

            throw new BusinessRuleException(

                    "PAYMENT_REQUIRED",

                    "Demo checkout requires a 100% coupon"
            );
        }

        // ======================================
        // CALCULATE MEMBERSHIP DATES
        // ======================================

        LocalDate today = LocalDate.now(clock);

        List<Membership> activeMemberships =
                memberships.findByUser_IdAndStatus(

                        userId,

                        MembershipStatus.ACTIVE
                );

        // Existing MembershipService.assign cancels
        // older active records. Protect future plans.

        boolean hasFutureMembership =
                activeMemberships.stream()
                        .anyMatch(
                                m -> m.getStartDate()
                                        .isAfter(today)
                        );

        if (hasFutureMembership) {

            throw new BusinessRuleException(

                    "FUTURE_MEMBERSHIP_EXISTS",

                    "A future membership is already scheduled"
            );
        }

        LocalDate currentEndDate =
                memberships.findActiveOn(userId, today)

                        .stream()

                        .map(Membership::getEndDate)

                        .max(LocalDate::compareTo)

                        .orElse(today.minusDays(1));

        LocalDate newEndDate =
                currentEndDate.plusDays(
                        paymentProperties.getMembershipDays()
                );

        String planName = "Demo Monthly";

        // ======================================
        // ACTIVATE OR EXTEND MEMBERSHIP
        // ======================================

        var membership = membershipService.assign(

                userId,

                new UpdateMembershipRequest(

                        planName,

                        today,

                        newEndDate,

                        MembershipStatus.ACTIVE
                ),

                userId
        );

        // ======================================
        // UPDATE COUPON USAGE
        // ======================================

        coupon.setUsed(
                coupon.getUsed() + 1
        );

        coupons.save(coupon);

        // ======================================
        // CREATE PAYMENT RECORD
        // ======================================

        DemoPayment payment = new DemoPayment();

        payment.setUserId(userId);

        payment.setCouponCode(coupon.getCode());

        payment.setOriginalPaise(
                quote.originalPaise()
        );

        payment.setDiscountPaise(
                quote.discountPaise()
        );

        payment.setPaidPaise(0);

        payment.setMembershipId(
                membership.id()
        );

        payment.setCreatedAt(
                Instant.now(clock)
        );

        payment = payments.saveAndFlush(payment);

        // ======================================
        // PUBLISH PAYMENT EMAIL EVENT
        // ======================================

        if (paymentProperties.isConfirmationEmailEnabled()) {

            eventPublisher.publishEvent(

                    new PaymentCompletedEvent(

                            payment.getId(),

                            user.getId(),

                            user.getEmail(),

                            user.getName(),

                            payment.getTransactionId(),

                            coupon.getCode(),

                            payment.getOriginalPaise(),

                            payment.getDiscountPaise(),

                            payment.getPaidPaise(),

                            planName,

                            today,

                            newEndDate
                    )
            );
        }

        return payment;
    }

    // Required for safe coupon creation with assigned IDs.

    private final jakarta.persistence.EntityManager entityManager;
}