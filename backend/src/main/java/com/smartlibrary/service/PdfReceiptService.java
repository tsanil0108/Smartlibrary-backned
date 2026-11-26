package com.smartlibrary.service;

import com.smartlibrary.dto.PaymentReceiptDto.ReceiptResponse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.springframework.stereotype.Service;

import java.awt.Color;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.math.BigDecimal;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.Locale;

@Service
public class PdfReceiptService {

    private static final Color PURPLE =
            new Color(99, 57, 239);

    private static final Color DARK =
            new Color(23, 20, 61);

    private static final Color MUTED =
            new Color(107, 114, 128);

    private static final Color LIGHT =
            new Color(245, 241, 255);

    private static final Color GREEN =
            new Color(22, 129, 91);

    private static final Color LIGHT_GREEN =
            new Color(235, 250, 241);

    private static final Color BORDER =
            new Color(229, 231, 235);

    private static final PDFont REGULAR =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );

    private static final PDFont BOLD =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
            );

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            );

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, hh:mm a",
                    Locale.ENGLISH
            ).withZone(ZoneId.of("Asia/Kolkata"));

    // ==========================================
    // GENERATE PDF
    // ==========================================

    public byte[] generateReceipt(
            ReceiptResponse receipt
    ) {

        try (
                PDDocument document = new PDDocument();

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            PDPage page = new PDPage(PDRectangle.A4);

            document.addPage(page);

            document.getDocumentInformation()
                    .setTitle("Smart Library Membership Receipt");

            document.getDocumentInformation()
                    .setAuthor("Smart Library");

            try (
                    PDPageContentStream content =
                            new PDPageContentStream(
                                    document,
                                    page
                            )
            ) {

                drawHeader(content);

                drawTitle(content, receipt);

                drawMemberDetails(content, receipt);

                drawMembershipDetails(content, receipt);

                drawPaymentDetails(content, receipt);

                drawTotal(content, receipt);

                drawFooter(content);
            }

            document.save(output);

            return output.toByteArray();

        } catch (IOException e) {

            throw new IllegalStateException(

                    "Unable to generate payment receipt.",

                    e
            );
        }
    }

    // ==========================================
    // HEADER
    // ==========================================

    private void drawHeader(
            PDPageContentStream c
    ) throws IOException {

        rectangle(c, 0, 744, 596, 98, DARK);

        rectangle(c, 40, 766, 48, 48, PURPLE);

        text(c, "SL", 50, 782, 21, BOLD, Color.WHITE);

        text(
                c,
                "SMART LIBRARY",
                104, 795, 24,
                BOLD, Color.WHITE
        );

        text(
                c,
                "Library Management System",
                105, 773, 11,
                REGULAR, Color.WHITE
        );
    }

    // ==========================================
    // TITLE
    // ==========================================

    private void drawTitle(
            PDPageContentStream c,
            ReceiptResponse r
    ) throws IOException {

        center(
                c,
                "MEMBERSHIP RECEIPT",
                710, 22, BOLD, DARK
        );

        center(
                c,
                r.receiptNumber(),
                688, 11, REGULAR, MUTED
        );

        rectangle(
                c,
                200, 648, 195, 25,
                LIGHT_GREEN
        );

        center(
                c,
                "PAYMENT SUCCESSFUL",
                657, 10, BOLD, GREEN
        );
    }

    // ==========================================
    // MEMBER DETAILS
    // ==========================================

    private void drawMemberDetails(
            PDPageContentStream c,
            ReceiptResponse r
    ) throws IOException {

        heading(c, "MEMBER DETAILS", 625);

        detail(
                c,
                "Member name",
                r.memberName(),
                598
        );

        detail(
                c,
                "Member ID",
                String.format(
                        Locale.ROOT,
                        "SLM-%06d",
                        r.userId()
                ),
                577
        );

        detail(
                c,
                "Email",
                r.email(),
                556
        );

        line(c, 541);
    }

    // ==========================================
    // MEMBERSHIP DETAILS
    // ==========================================

    private void drawMembershipDetails(
            PDPageContentStream c,
            ReceiptResponse r
    ) throws IOException {

        heading(c, "MEMBERSHIP DETAILS", 520);

        detail(
                c,
                "Membership plan",
                r.membershipPlan(),
                493
        );

        detail(
                c,
                "Valid from",
                r.membershipStartDate() == null
                        ? "-"
                        : DATE.format(r.membershipStartDate()),
                472
        );

        detail(
                c,
                "Valid until",
                r.membershipEndDate() == null
                        ? "-"
                        : DATE.format(r.membershipEndDate()),
                451
        );

        line(c, 436);
    }

    // ==========================================
    // PAYMENT DETAILS
    // ==========================================

    private void drawPaymentDetails(
            PDPageContentStream c,
            ReceiptResponse r
    ) throws IOException {

        heading(c, "PAYMENT DETAILS", 415);

        detail(
                c,
                "Transaction ID",
                r.transactionId(),
                388
        );

        detail(
                c,
                "Payment date",
                r.paymentDate() == null
                        ? "-"
                        : DATE_TIME.format(r.paymentDate()) + " IST",
                367
        );

        detail(
                c,
                "Payment method",
                paymentMethod(r.paymentMethod()),
                346
        );

        detail(
                c,
                "Coupon code",
                r.couponCode(),
                325
        );

        detail(
                c,
                "Original price",
                money(r.originalPaise()),
                304
        );

        detail(
                c,
                "Coupon discount",
                money(r.discountPaise()),
                283
        );

        line(c, 266);
    }

    // ==========================================
    // TOTAL
    // ==========================================

    private void drawTotal(
            PDPageContentStream c,
            ReceiptResponse r
    ) throws IOException {

        rectangle(
                c,
                40, 192, 515, 57,
                LIGHT
        );

        text(
                c,
                "TOTAL PAID",
                56, 214, 13,
                BOLD, PURPLE
        );

        right(
                c,
                money(r.paidPaise()),
                538, 212, 23,
                BOLD, PURPLE
        );

        String note = r.paidPaise() == 0

                ? "Membership activated using a 100% discount coupon."

                : "Payment successfully verified by Smart Library.";

        center(
                c,
                note,
                169, 10,
                REGULAR, GREEN
        );
    }

    // ==========================================
    // FOOTER
    // ==========================================

    private void drawFooter(
            PDPageContentStream c
    ) throws IOException {

        line(c, 125);

        center(
                c,
                "Thank you for being a Smart Library member.",
                104, 11,
                BOLD, DARK
        );

        center(
                c,
                "This is a computer-generated payment receipt.",
                85, 9,
                REGULAR, MUTED
        );

        center(
                c,
                "No physical signature is required.",
                69, 9,
                REGULAR, MUTED
        );

        rectangle(c, 0, 0, 596, 8, PURPLE);
    }

    // ==========================================
    // FORMATTING HELPERS
    // ==========================================

    private String money(long paise) {

        return "INR " + BigDecimal

                .valueOf(paise, 2)

                .toPlainString();
    }

    private String paymentMethod(String method) {

        if (method == null) {
            return "-";
        }

        return switch (
                method.toUpperCase(Locale.ROOT)
                ) {

            case "UPI" -> "UPI";

            case "CARD" -> "Credit / Debit Card";

            case "NETBANKING" -> "Net Banking";

            case "WALLET" -> "Wallet";

            case "COUPON", "DEMO_COUPON" ->
                    "Coupon Redemption";

            case "RAZORPAY" -> "Razorpay";

            default -> method;
        };
    }

    // Standard Helvetica does not support all Unicode.
    // Replace unsupported characters safely.

    private String safeText(
            String value,
            PDFont font
    ) {

        if (value == null || value.isBlank()) {
            return "-";
        }

        StringBuilder result = new StringBuilder();

        value.codePoints().forEach(cp -> {

            String character =
                    new String(Character.toChars(cp));

            if (Character.isISOControl(cp)) {

                result.append(" ");

                return;
            }

            try {

                font.encode(character);

                result.append(character);

            } catch (
                    IOException |
                    IllegalArgumentException ex
            ) {

                result.append("?");
            }
        });

        return result.toString();
    }

    private String fitText(
            String value,
            PDFont font,
            float size,
            float maxWidth
    ) throws IOException {

        String clean = safeText(value, font);

        if (width(clean, font, size) <= maxWidth) {
            return clean;
        }

        while (!clean.isEmpty()

                && width(
                clean + "...",
                font,
                size
        ) > maxWidth) {

            clean = clean.substring(
                    0,
                    clean.length() - 1
            );
        }

        return clean + "...";
    }

    private float width(
            String value,
            PDFont font,
            float size
    ) throws IOException {

        return font.getStringWidth(value)
                / 1000f * size;
    }

    // ==========================================
    // DRAWING HELPERS
    // ==========================================

    private void heading(
            PDPageContentStream c,
            String value,
            float y
    ) throws IOException {

        text(
                c, value,
                40, y, 11,
                BOLD, PURPLE
        );
    }

    private void detail(
            PDPageContentStream c,
            String label,
            String value,
            float y
    ) throws IOException {

        text(
                c, label,
                40, y, 10,
                REGULAR, MUTED
        );

        String fitted = fitText(
                value,
                BOLD,
                10,
                320
        );

        right(
                c, fitted,
                555, y, 10,
                BOLD, DARK
        );
    }

    private void rectangle(
            PDPageContentStream c,
            float x,
            float y,
            float w,
            float h,
            Color color
    ) throws IOException {

        c.setNonStrokingColor(color);

        c.addRect(x, y, w, h);

        c.fill();
    }

    private void line(
            PDPageContentStream c,
            float y
    ) throws IOException {

        c.setStrokingColor(BORDER);

        c.setLineWidth(0.7f);

        c.moveTo(40, y);

        c.lineTo(555, y);

        c.stroke();
    }

    private void text(
            PDPageContentStream c,
            String value,
            float x,
            float y,
            float size,
            PDFont font,
            Color color
    ) throws IOException {

        c.beginText();

        c.setFont(font, size);

        c.setNonStrokingColor(color);

        c.newLineAtOffset(x, y);

        c.showText(safeText(value, font));

        c.endText();
    }

    private void center(
            PDPageContentStream c,
            String value,
            float y,
            float size,
            PDFont font,
            Color color
    ) throws IOException {

        String clean = safeText(value, font);

        float x = (
                PDRectangle.A4.getWidth()
                        - width(clean, font, size)
        ) / 2;

        text(c, clean, x, y, size, font, color);
    }

    private void right(
            PDPageContentStream c,
            String value,
            float x,
            float y,
            float size,
            PDFont font,
            Color color
    ) throws IOException {

        String clean = safeText(value, font);

        text(
                c,
                clean,
                x - width(clean, font, size),
                y,
                size,
                font,
                color
        );
    }
}