package com.smartlibrary.controller;

import com.smartlibrary.dto.PaymentReceiptDto.ReceiptResponse;

import com.smartlibrary.security.UserPrincipal;

import com.smartlibrary.service.PaymentReceiptService;
import com.smartlibrary.service.PdfReceiptService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentReceiptController {

    private final PaymentReceiptService receiptService;

    private final PdfReceiptService pdfService;

    // ==========================================
    // MEMBER RECEIPT DETAILS
    // ==========================================

    @GetMapping("/payments/{paymentId}/receipt/details")
    @PreAuthorize("hasRole('MEMBER')")
    public ReceiptResponse getMemberReceiptDetails(

            @PathVariable Long paymentId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        return receiptService.getMemberReceipt(

                paymentId,

                me.getId()
        );
    }

    // ==========================================
    // MEMBER DOWNLOAD PDF RECEIPT
    // ==========================================

    @GetMapping("/payments/{paymentId}/receipt")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<byte[]> downloadMemberReceipt(

            @PathVariable Long paymentId,

            @AuthenticationPrincipal UserPrincipal me

    ) {

        ReceiptResponse receipt =
                receiptService.getMemberReceipt(

                        paymentId,

                        me.getId()
                );

        return buildPdfResponse(receipt);
    }

    // ==========================================
    // ADMIN RECEIPT DETAILS
    // ==========================================

    @GetMapping("/admin/payments/{paymentId}/receipt/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ReceiptResponse getAdminReceiptDetails(

            @PathVariable Long paymentId

    ) {

        return receiptService.getAdminReceipt(
                paymentId
        );
    }

    // ==========================================
    // ADMIN DOWNLOAD PDF RECEIPT
    // ==========================================

    @GetMapping("/admin/payments/{paymentId}/receipt")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadAdminReceipt(

            @PathVariable Long paymentId

    ) {

        ReceiptResponse receipt =
                receiptService.getAdminReceipt(
                        paymentId
                );

        return buildPdfResponse(receipt);
    }

    // ==========================================
    // BUILD PDF DOWNLOAD RESPONSE
    // ==========================================

    private ResponseEntity<byte[]> buildPdfResponse(

            ReceiptResponse receipt

    ) {

        byte[] pdf =
                pdfService.generateReceipt(receipt);

        String fileName =
                receipt.receiptNumber() + ".pdf";

        ContentDisposition disposition =
                ContentDisposition.attachment()

                        .filename(fileName)

                        .build();

        return ResponseEntity.ok()

                .contentType(
                        MediaType.APPLICATION_PDF
                )

                .header(

                        HttpHeaders.CONTENT_DISPOSITION,

                        disposition.toString()
                )

                .header(

                        "X-Content-Type-Options",

                        "nosniff"
                )

                .cacheControl(
                        CacheControl.noStore()
                )

                .contentLength(pdf.length)

                .body(pdf);
    }
}