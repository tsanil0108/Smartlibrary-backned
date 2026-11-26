package com.smartlibrary.repository;

import com.smartlibrary.entity.DemoPayment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoPaymentRepository
        extends JpaRepository<DemoPayment, Long> {

    // Member payment history

    List<DemoPayment> findByUserIdOrderByIdDesc(
            Long userId
    );

    // Duplicate coupon protection

    boolean existsByUserIdAndCouponCode(
            Long userId,
            String couponCode
    );

    Optional<DemoPayment> findByUserIdAndCouponCode(
            Long userId,
            String couponCode
    );

    // Member receipt access

    Optional<DemoPayment> findByIdAndUserId(
            Long id,
            Long userId
    );

    // Admin payment history

    List<DemoPayment> findAllByOrderByIdDesc();

    // Member analytics

    long countByUserId(
            Long userId
    );

    // ==========================================
    // REAL PAYMENT METHODS
    // ==========================================

    Optional<DemoPayment> findByPaymentOrderId(
            Long paymentOrderId
    );

    Optional<DemoPayment> findByRazorpayOrderId(
            String razorpayOrderId
    );

    Optional<DemoPayment> findByRazorpayPaymentId(
            String razorpayPaymentId
    );

    boolean existsByPaymentOrderId(
            Long paymentOrderId
    );

    boolean existsByRazorpayPaymentId(
            String razorpayPaymentId
    );

    List<DemoPayment> findByStoredStatusOrderByIdDesc(
            String storedStatus
    );
}