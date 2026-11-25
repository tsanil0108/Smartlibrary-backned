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

    // Check duplicate coupon usage

    boolean existsByUserIdAndCouponCode(
            Long userId,
            String couponCode
    );

    // Find existing payment for duplicate requests

    Optional<DemoPayment> findByUserIdAndCouponCode(
            Long userId,
            String couponCode
    );

    // Find payment belonging to specific member

    Optional<DemoPayment> findByIdAndUserId(
            Long id,
            Long userId
    );

    // Admin payment history

    List<DemoPayment> findAllByOrderByIdDesc();

    // Count member payments

    long countByUserId(Long userId);
}