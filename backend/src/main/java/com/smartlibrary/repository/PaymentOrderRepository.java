package com.smartlibrary.repository;

import com.smartlibrary.entity.PaymentOrder;
import com.smartlibrary.entity.PaymentOrderStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository
        extends JpaRepository<PaymentOrder, Long> {

    // ==========================================
    // FIND ORDER BY REFERENCE
    // ==========================================

    Optional<PaymentOrder> findByOrderReference(
            String orderReference
    );

    // ==========================================
    // FIND RAZORPAY ORDER
    // ==========================================

    Optional<PaymentOrder> findByRazorpayOrderId(
            String razorpayOrderId
    );

    // ==========================================
    // FIND RAZORPAY PAYMENT
    // ==========================================

    Optional<PaymentOrder> findByRazorpayPaymentId(
            String razorpayPaymentId
    );

    // ==========================================
    // FIND MEMBER PAYMENT ORDER
    // ==========================================

    Optional<PaymentOrder> findByIdAndUserId(
            Long id,
            Long userId
    );

    // ==========================================
    // MEMBER PAYMENT ORDER HISTORY
    // ==========================================

    List<PaymentOrder> findByUserIdOrderByIdDesc(
            Long userId
    );

    // ==========================================
    // ADMIN PAYMENT ORDER LIST
    // ==========================================

    Page<PaymentOrder> findAllByOrderByIdDesc(
            Pageable pageable
    );

    // ==========================================
    // FILTER ORDERS BY STATUS
    // ==========================================

    Page<PaymentOrder> findByStatusOrderByIdDesc(
            PaymentOrderStatus status,
            Pageable pageable
    );

    // ==========================================
    // DUPLICATE PAYMENT CHECK
    // ==========================================

    boolean existsByRazorpayPaymentId(
            String razorpayPaymentId
    );

    // ==========================================
    // COUNT ORDERS BY STATUS
    // ==========================================

    long countByStatus(
            PaymentOrderStatus status
    );

    // ==========================================
    // LOCK ORDER DURING PAYMENT VERIFICATION
    // ==========================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PaymentOrder p
            WHERE p.id = :id
            """)
    Optional<PaymentOrder> findByIdForUpdate(
            @Param("id") Long id
    );

    // ==========================================
    // LOCK ORDER DURING WEBHOOK PROCESSING
    // ==========================================

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PaymentOrder p
            WHERE p.razorpayOrderId = :orderId
            """)
    Optional<PaymentOrder> findByRazorpayOrderIdForUpdate(
            @Param("orderId") String orderId
    );

    // ==========================================
    // FIND LOCALLY EXPIRED PAYMENT ORDERS
    // ==========================================

    List<PaymentOrder> findByStatusInAndExpiresAtBefore(
            List<PaymentOrderStatus> statuses,
            Instant expiresAt
    );

    // ==========================================
    // COUPON RESERVATION CHECK
    // ==========================================

    List<PaymentOrder> findByCouponCodeAndStatusIn(
            String couponCode,
            List<PaymentOrderStatus> statuses
    );

    // ==========================================
    // PAYMENT RECOVERY - PAGINATION
    // ==========================================

    Page<PaymentOrder>
    findByStatusInAndRazorpayOrderIdIsNotNullOrderByIdAsc(

            List<PaymentOrderStatus> statuses,

            Pageable pageable
    );

    // ==========================================
    // PAYMENT RECOVERY - CURSOR BASED PAGINATION
    // ==========================================

    /*
     * Used by PaymentRecoveryScheduler.
     *
     * Processes pending and captured orders in batches.
     * Skips orders without a Razorpay order ID.
     *
     * Cursor pagination prevents the scheduler from
     * repeatedly processing only the first 50 orders.
     */

    Page<PaymentOrder>
    findByIdGreaterThanAndStatusInAndRazorpayOrderIdIsNotNullOrderByIdAsc(

            Long id,

            List<PaymentOrderStatus> statuses,

            Pageable pageable
    );
}