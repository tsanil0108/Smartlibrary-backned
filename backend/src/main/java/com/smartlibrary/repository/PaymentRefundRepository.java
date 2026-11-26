package com.smartlibrary.repository;

import com.smartlibrary.entity.PaymentRefund;
import com.smartlibrary.entity.RefundStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRefundRepository
        extends JpaRepository<PaymentRefund, Long> {

    Optional<PaymentRefund> findByPaymentId(
            Long paymentId
    );

    Optional<PaymentRefund> findByPaymentOrderId(
            Long paymentOrderId
    );

    Optional<PaymentRefund> findByRefundReference(
            String refundReference
    );

    Optional<PaymentRefund> findByRazorpayRefundId(
            String razorpayRefundId
    );

    boolean existsByPaymentId(
            Long paymentId
    );

    long countByStatus(
            RefundStatus status
    );

    List<PaymentRefund> findByUserIdOrderByIdDesc(
            Long userId
    );

    Page<PaymentRefund> findAllByOrderByIdDesc(
            Pageable pageable
    );

    Page<PaymentRefund> findByStatusOrderByIdDesc(
            RefundStatus status,
            Pageable pageable
    );

    Page<PaymentRefund>
    findByStatusInOrderByIdAsc(
            List<RefundStatus> statuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM PaymentRefund r
            WHERE r.id = :id
            """)
    Optional<PaymentRefund> findByIdForUpdate(
            @Param("id") Long id
    );

    @Query("""
            SELECT COALESCE(SUM(r.amountPaise), 0)
            FROM PaymentRefund r
            WHERE r.status = :status
            """)
    long sumAmountByStatus(
            @Param("status") RefundStatus status
    );
}