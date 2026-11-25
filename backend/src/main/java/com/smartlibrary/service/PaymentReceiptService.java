package com.smartlibrary.service;

import com.smartlibrary.dto.PaymentReceiptDto.ReceiptResponse;

import com.smartlibrary.entity.DemoPayment;
import com.smartlibrary.entity.Membership;
import com.smartlibrary.entity.User;

import com.smartlibrary.exception.BusinessRuleException;
import com.smartlibrary.exception.NotFoundException;

import com.smartlibrary.repository.DemoPaymentRepository;
import com.smartlibrary.repository.MembershipRepository;
import com.smartlibrary.repository.UserRepository;

import java.util.Locale;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReceiptService {

    private final DemoPaymentRepository paymentRepository;

    private final UserRepository userRepository;

    private final MembershipRepository membershipRepository;

    // ==========================================
    // MEMBER RECEIPT DETAILS
    // ==========================================

    @Transactional(readOnly = true)
    public ReceiptResponse getMemberReceipt(
            Long paymentId,
            Long userId
    ) {

        DemoPayment payment =
                paymentRepository
                        .findByIdAndUserId(
                                paymentId,
                                userId
                        )
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Payment",
                                        paymentId
                                )
                        );

        return buildReceipt(payment);
    }

    // ==========================================
    // ADMIN RECEIPT DETAILS
    // ==========================================

    @Transactional(readOnly = true)
    public ReceiptResponse getAdminReceipt(
            Long paymentId
    ) {

        DemoPayment payment =
                paymentRepository.findById(paymentId)
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Payment",
                                        paymentId
                                )
                        );

        return buildReceipt(payment);
    }

    // ==========================================
    // BUILD RECEIPT RESPONSE
    // ==========================================

    private ReceiptResponse buildReceipt(
            DemoPayment payment
    ) {

        if (!payment.isFreeRedemption()) {

            throw new BusinessRuleException(

                    "RECEIPT_UNAVAILABLE",

                    "Receipt is available only for "
                            + "successful coupon redemptions"
            );
        }

        User user =
                userRepository.findById(
                                payment.getUserId()
                        )
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "User",
                                        payment.getUserId()
                                )
                        );

        Membership membership =
                membershipRepository.findById(
                                payment.getMembershipId()
                        )
                        .orElseThrow(
                                () -> new NotFoundException(
                                        "Membership",
                                        payment.getMembershipId()
                                )
                        );

        // Validate membership ownership

        if (!Objects.equals(

                membership.getUser().getId(),

                payment.getUserId()

        )) {

            throw new BusinessRuleException(

                    "INVALID_PAYMENT_MEMBERSHIP",

                    "Payment membership details are invalid"
            );
        }

        String receiptNumber =
                String.format(

                        Locale.ROOT,

                        "SLM-RCP-%08d",

                        payment.getId()
                );

        return new ReceiptResponse(

                payment.getId(),

                receiptNumber,

                payment.getTransactionId(),

                user.getId(),

                user.getName(),

                user.getEmail(),

                payment.getCouponCode(),

                payment.getOriginalPaise(),

                payment.getDiscountPaise(),

                payment.getPaidPaise(),

                membership.getId(),

                membership.getPlanName(),

                membership.getStartDate(),

                membership.getEndDate(),

                payment.getPaymentMethod(),

                payment.getStatus(),

                payment.getCreatedAt()
        );
    }
}