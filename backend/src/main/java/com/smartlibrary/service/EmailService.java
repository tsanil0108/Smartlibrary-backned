package com.smartlibrary.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            );

    // ==========================================
    // BOOK BORROW CONFIRMATION
    // ==========================================

    public void sendBorrowConfirmation(
            String email,
            String memberName,
            String bookTitle,
            LocalDate borrowDate,
            LocalDate dueDate
    ) throws MessagingException {

        sendLoanNotification(
                email,
                memberName,
                bookTitle,
                "Book Borrowed Successfully",
                "Your book has been successfully borrowed. "
                        + "Please return it before or on the due date.",
                borrowDate,
                dueDate,
                "BORROWED"
        );
    }

    // ==========================================
    // GENERAL LOAN NOTIFICATION
    // ==========================================

    public void sendLoanNotification(
            String email,
            String memberName,
            String bookTitle,
            String subject,
            String description,
            LocalDate eventDate,
            LocalDate dueDate,
            String status
    ) throws MessagingException {

        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, initial-scale=1.0">
            </head>

            <body style="margin:0;
                         padding:24px;
                         background:#f1f5f9;
                         font-family:Arial,sans-serif;">

                <div style="max-width:600px;
                            margin:auto;
                            background:#ffffff;
                            border-radius:18px;
                            overflow:hidden;
                            border:1px solid #e2e8f0;">

                    <div style="background:#2563eb;
                                color:#ffffff;
                                padding:35px 20px;
                                text-align:center;">

                        <h1 style="margin:0;font-size:28px;">
                            Smart Library
                        </h1>

                        <p style="color:#dbeafe;">
                            Read more, discover more.
                        </p>

                    </div>

                    <div style="padding:30px;
                                color:#172554;">

                        <h2 style="color:#2563eb;
                                   font-size:24px;">
                            %s
                        </h2>

                        <p>Dear %s,</p>

                        <p style="line-height:1.8;
                                  color:#475569;">
                            %s
                        </p>

                        <div style="background:#f8fafc;
                                    padding:22px;
                                    border-radius:12px;
                                    border:1px solid #e2e8f0;">

                            <table style="width:100%%;
                                          border-collapse:collapse;
                                          font-size:14px;">

                                <tr>
                                    <td style="padding:12px 0;">
                                        Book Title
                                    </td>
                                    <td style="text-align:right;">
                                        <b>%s</b>
                                    </td>
                                </tr>

                                <tr>
                                    <td style="padding:12px 0;">
                                        Activity Date
                                    </td>
                                    <td style="text-align:right;">
                                        <b>%s</b>
                                    </td>
                                </tr>

                                <tr>
                                    <td style="padding:12px 0;">
                                        Due Date
                                    </td>
                                    <td style="text-align:right;
                                               color:#2563eb;">
                                        <b>%s</b>
                                    </td>
                                </tr>

                                <tr>
                                    <td style="padding:12px 0;">
                                        Status
                                    </td>
                                    <td style="text-align:right;">
                                        <b>%s</b>
                                    </td>
                                </tr>

                            </table>

                        </div>

                        <div style="text-align:center;
                                    margin:35px 0;">

                            <a href="%s/member/loans"
                               style="display:inline-block;
                                      background:#2563eb;
                                      color:#ffffff;
                                      padding:15px 28px;
                                      border-radius:10px;
                                      text-decoration:none;
                                      font-weight:bold;">

                                View My Loans

                            </a>

                        </div>

                        <p>Happy Reading!</p>

                        <p>
                            <b>Smart Library Team</b>
                        </p>

                    </div>

                    <div style="background:#f8fafc;
                                padding:22px;
                                text-align:center;
                                color:#64748b;
                                font-size:12px;
                                line-height:1.8;">

                        Smart Library Management System<br>

                        This is an automated email notification.<br>

                        Please do not reply to this email.

                    </div>

                </div>

            </body>
            </html>
            """.formatted(
                escape(subject),
                escape(memberName),
                escape(description),
                escape(bookTitle),
                eventDate.format(DATE_FORMAT),
                dueDate.format(DATE_FORMAT),
                escape(status),
                escape(frontendUrl)
        );

        sendHtmlEmail(
                email,
                subject + " - Smart Library",
                html
        );
    }

    // ==========================================
    // SEND HTML EMAIL
    // ==========================================

    public void sendHtmlEmail(
            String email,
            String subject,
            String html
    ) throws MessagingException {

        MimeMessage message =
                mailSender.createMimeMessage();

        MimeMessageHelper helper =
                new MimeMessageHelper(
                        message,
                        true,
                        "UTF-8"
                );

        helper.setFrom(senderEmail);

        helper.setTo(email);

        helper.setSubject(subject);

        helper.setText(html, true);

        mailSender.send(message);
    }

    private String escape(String value) {

        return HtmlUtils.htmlEscape(
                value == null ? "" : value
        );
    }
}