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

    private static final Color BLUE =
            new Color(37, 99, 235);

    private static final Color DARK_BLUE =
            new Color(30, 58, 138);

    private static final Color DARK =
            new Color(30, 41, 59);

    private static final Color MUTED =
            new Color(100, 116, 139);

    private static final Color LIGHT_BLUE =
            new Color(239, 246, 255);

    private static final Color LIGHT_GREEN =
            new Color(240, 253, 244);

    private static final Color GREEN =
            new Color(22, 101, 52);

    private static final Color BORDER =
            new Color(226, 232, 240);

    private static final Color WHITE =
            Color.WHITE;

    private static final PDFont REGULAR =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );

    private static final PDFont BOLD =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
            );

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            );

    // ==========================================
    // GENERATE PDF RECEIPT
    // ==========================================

    public byte[] generateReceipt(
            ReceiptResponse receipt
    ) {

        try (

                PDDocument document = new PDDocument();

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()

        ) {

            PDPage page =
                    new PDPage(PDRectangle.A4);

            document.addPage(page);

            document.getDocumentInformation()
                    .setTitle(
                            "Smart Library Membership Receipt"
                    );

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

                drawReceiptTitle(content);

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

                    "Unable to generate payment receipt PDF",

                    e
            );
        }
    }

    // ==========================================
    // HEADER
    // ==========================================

    private void drawHeader(
            PDPageContentStream content
    ) throws IOException {

        fillRectangle(

                content,

                0, 732, 595, 110,

                DARK_BLUE
        );

        fillRectangle(

                content,

                40, 761, 48, 48,

                BLUE
        );

        text(

                content,

                "SL",

                49, 778,

                22,

                BOLD,

                WHITE
        );

        text(

                content,

                "SMART LIBRARY",

                104, 791,

                24,

                BOLD,

                WHITE
        );

        text(

                content,

                "Library Management System",

                105, 769,

                11,

                REGULAR,

                WHITE
        );
    }

    // ==========================================
    // RECEIPT TITLE
    // ==========================================

    private void drawReceiptTitle(
            PDPageContentStream content
    ) throws IOException {

        centerText(

                content,

                "MEMBERSHIP RECEIPT",

                695,

                23,

                BOLD,

                DARK
        );

        centerText(

                content,

                "Demo coupon redemption",

                674,

                11,

                REGULAR,

                MUTED
        );

        fillRectangle(

                content,

                197, 638, 201, 24,

                LIGHT_GREEN
        );

        centerText(

                content,

                "SUCCESSFULLY REDEEMED",

                646,

                10,

                BOLD,

                GREEN
        );
    }

    // ==========================================
    // MEMBER DETAILS
    // ==========================================

    private void drawMemberDetails(

            PDPageContentStream content,

            ReceiptResponse receipt

    ) throws IOException {

        text(

                content,

                "RECEIPT DETAILS",

                40, 612,

                12,

                BOLD,

                DARK_BLUE
        );

        line(content, 40, 602, 555, 602);

        String paymentDate =

                receipt.paymentDate() == null

                        ? "Not available"

                        : receipt.paymentDate()

                        .atZone(
                                ZoneId.of("Asia/Kolkata")
                        )

                        .format(DATE_FORMAT);

        detail(

                content,

                "Receipt Number",

                receipt.receiptNumber(),

                40, 581
        );

        detail(

                content,

                "Transaction ID",

                receipt.transactionId(),

                310, 581
        );

        detail(

                content,

                "Member Name",

                receipt.memberName(),

                40, 539
        );

        detail(

                content,

                "Member ID",

                String.valueOf(receipt.userId()),

                310, 539
        );

        detail(

                content,

                "Email Address",

                receipt.email(),

                40, 497
        );

        detail(

                content,

                "Receipt Date",

                paymentDate,

                310, 497
        );
    }

    // ==========================================
    // MEMBERSHIP DETAILS
    // ==========================================

    private void drawMembershipDetails(

            PDPageContentStream content,

            ReceiptResponse receipt

    ) throws IOException {

        fillRectangle(

                content,

                40, 355, 515, 105,

                LIGHT_BLUE
        );

        text(

                content,

                "MEMBERSHIP DETAILS",

                55, 439,

                12,

                BOLD,

                DARK_BLUE
        );

        detail(

                content,

                "Membership Plan",

                receipt.membershipPlan(),

                55, 417
        );

        detail(

                content,

                "Membership ID",

                String.valueOf(
                        receipt.membershipId()
                ),

                310, 417
        );

        detail(

                content,

                "Start Date",

                receipt.membershipStartDate()
                        .format(DATE_FORMAT),

                55, 380
        );

        detail(

                content,

                "End Date",

                receipt.membershipEndDate()
                        .format(DATE_FORMAT),

                310, 380
        );
    }

    // ==========================================
    // PAYMENT DETAILS
    // ==========================================

    private void drawPaymentDetails(

            PDPageContentStream content,

            ReceiptResponse receipt

    ) throws IOException {

        fillRectangle(

                content,

                40, 309, 515, 30,

                DARK_BLUE
        );

        text(

                content,

                "DESCRIPTION",

                53, 320,

                11,

                BOLD,

                WHITE
        );

        rightText(

                content,

                "AMOUNT",

                540, 320,

                11,

                BOLD,

                WHITE
        );

        text(

                content,

                "Membership Amount",

                53, 287,

                11,

                REGULAR,

                DARK
        );

        rightText(

                content,

                money(receipt.originalPaise()),

                540, 287,

                11,

                BOLD,

                DARK
        );

        line(content, 40, 274, 555, 274);

        text(

                content,

                "Coupon Discount (100%)",

                53, 253,

                11,

                REGULAR,

                DARK
        );

        rightText(

                content,

                "- " + money(receipt.discountPaise()),

                540, 253,

                11,

                BOLD,

                GREEN
        );

        line(content, 40, 240, 555, 240);

        text(

                content,

                "Coupon Code",

                53, 219,

                11,

                REGULAR,

                DARK
        );

        rightText(

                content,

                receipt.couponCode(),

                540, 219,

                11,

                BOLD,

                DARK
        );
    }

    // ==========================================
    // TOTAL AMOUNT
    // ==========================================

    private void drawTotal(

            PDPageContentStream content,

            ReceiptResponse receipt

    ) throws IOException {

        fillRectangle(

                content,

                40, 139, 515, 57,

                LIGHT_GREEN
        );

        text(

                content,

                "TOTAL AMOUNT PAID",

                55, 161,

                12,

                BOLD,

                GREEN
        );

        rightText(

                content,

                money(receipt.paidPaise()),

                540, 158,

                24,

                BOLD,

                GREEN
        );

        text(

                content,

                "Payment Method: "
                        + receipt.paymentMethod(),

                40, 120,

                10,

                REGULAR,

                MUTED
        );
    }

    // ==========================================
    // FOOTER
    // ==========================================

    private void drawFooter(
            PDPageContentStream content
    ) throws IOException {

        line(content, 40, 104, 555, 104);

        centerText(

                content,

                "Thank you for using Smart Library!",

                85,

                12,

                BOLD,

                DARK_BLUE
        );

        centerText(

                content,

                "No money was charged for this coupon redemption.",

                66,

                9,

                REGULAR,

                MUTED
        );

        centerText(

                content,

                "Computer-generated receipt. No signature required.",

                51,

                9,

                REGULAR,

                MUTED
        );

        centerText(

                content,

                "This is not a tax invoice.",

                36,

                9,

                REGULAR,

                MUTED
        );
    }

    // ==========================================
    // DRAW DETAIL LABEL AND VALUE
    // ==========================================

    private void detail(

            PDPageContentStream content,

            String label,

            String value,

            float x,

            float y

    ) throws IOException {

        text(

                content,

                label,

                x, y,

                9,

                REGULAR,

                MUTED
        );

        text(

                content,

                fitText(value, BOLD, 11, 235),

                x, y - 16,

                11,

                BOLD,

                DARK
        );
    }

    // ==========================================
    // DRAW TEXT
    // ==========================================

    private void text(

            PDPageContentStream content,

            String value,

            float x,

            float y,

            float size,

            PDFont font,

            Color color

    ) throws IOException {

        content.beginText();

        content.setFont(font, size);

        content.setNonStrokingColor(color);

        content.newLineAtOffset(x, y);

        content.showText(safeText(value));

        content.endText();
    }

    // ==========================================
    // CENTER TEXT
    // ==========================================

    private void centerText(

            PDPageContentStream content,

            String value,

            float y,

            float size,

            PDFont font,

            Color color

    ) throws IOException {

        String safe = safeText(value);

        float width =
                font.getStringWidth(safe)
                        / 1000 * size;

        float x = (595 - width) / 2;

        text(

                content,

                safe,

                x, y,

                size,

                font,

                color
        );
    }

    // ==========================================
    // RIGHT ALIGNED TEXT
    // ==========================================

    private void rightText(

            PDPageContentStream content,

            String value,

            float right,

            float y,

            float size,

            PDFont font,

            Color color

    ) throws IOException {

        String safe = safeText(value);

        float width =
                font.getStringWidth(safe)
                        / 1000 * size;

        text(

                content,

                safe,

                right - width,

                y,

                size,

                font,

                color
        );
    }

    // ==========================================
    // DRAW BACKGROUND RECTANGLE
    // ==========================================

    private void fillRectangle(

            PDPageContentStream content,

            float x,

            float y,

            float width,

            float height,

            Color color

    ) throws IOException {

        content.setNonStrokingColor(color);

        content.addRect(

                x,

                y,

                width,

                height
        );

        content.fill();
    }

    // ==========================================
    // DRAW SEPARATOR LINE
    // ==========================================

    private void line(

            PDPageContentStream content,

            float x1,

            float y1,

            float x2,

            float y2

    ) throws IOException {

        content.setStrokingColor(BORDER);

        content.setLineWidth(0.8f);

        content.moveTo(x1, y1);

        content.lineTo(x2, y2);

        content.stroke();
    }

    // ==========================================
    // FORMAT MONEY
    // ==========================================

    private String money(long paise) {

        return "INR "

                + BigDecimal.valueOf(paise, 2)
                .toPlainString();
    }

    // ==========================================
    // HANDLE UNSUPPORTED CHARACTERS
    // ==========================================

    private String safeText(String value) {

        if (value == null || value.isBlank()) {

            return "N/A";
        }

        return value

                .replace("\n", " ")

                .replace("\r", " ")

                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    // ==========================================
    // PREVENT LONG TEXT OVERFLOW
    // ==========================================

    private String fitText(

            String value,

            PDFont font,

            float fontSize,

            float maxWidth

    ) throws IOException {

        String result = safeText(value);

        if (font.getStringWidth(result)
                / 1000 * fontSize <= maxWidth) {

            return result;
        }

        while (!result.isEmpty()

                && font.getStringWidth(result + "...")
                / 1000 * fontSize > maxWidth) {

            result = result.substring(
                    0,
                    result.length() - 1
            );
        }

        return result + "...";
    }
}