package com.smartlibrary.controller;

import com.smartlibrary.entity.DemoPayment;
import com.smartlibrary.repository.DemoPaymentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final DemoPaymentRepository paymentRepository;

    public AdminPaymentController(
            DemoPaymentRepository paymentRepository
    ) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    public List<DemoPayment> getAllPayments() {

        return paymentRepository.findAll(
                Sort.by(Sort.Direction.DESC, "id")
        );

    }
}