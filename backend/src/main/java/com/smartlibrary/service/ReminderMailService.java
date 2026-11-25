package com.smartlibrary.service;

import com.smartlibrary.config.EmailReminderProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class ReminderMailService {

    private final JavaMailSender mailSender;

    private final EmailReminderProperties properties;

    public void sendEmail(
            String recipient,
            String memberName,
            String subject,
            String message
    ) {

        try {

            MimeMessage mimeMessage =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            mimeMessage,
                            true,
                            "UTF-8"
                    );

            helper.setFrom(
                    properties.getSender()
            );

            helper.setTo(recipient);

            helper.setSubject(subject);

            helper.setText(
                    buildEmailTemplate(
                            memberName,
                            subject,
                            message
                    ),
                    true
            );

            mailSender.send(mimeMessage);

        } catch (MessagingException e) {

            throw new IllegalStateException(
                    "Unable to prepare reminder email",
                    e
            );
        }
    }

    private String buildEmailTemplate(
            String memberName,
            String subject,
            String message
    ) {

        String safeName =
                HtmlUtils.htmlEscape(memberName);

        String safeSubject =
                HtmlUtils.htmlEscape(subject);

        String safeMessage =
                HtmlUtils.htmlEscape(message)
                        .replace("\n", "<br>");

        return """
                <!DOCTYPE html>
                <html>
                <body style="
                    margin:0;
                    padding:25px;
                    background:#f1f5f9;
                    font-family:Arial,sans-serif;
                ">

                    <div style="
                        max-width:600px;
                        margin:auto;
                        background:#ffffff;
                        border-radius:12px;
                        overflow:hidden;
                    ">

                        <div style="
                            background:#2563eb;
                            padding:30px;
                            text-align:center;
                            color:white;
                        ">

                            <h1 style="margin:0;">
                                Smart Library
                            </h1>

                            <p>
                                Library Management System
                            </p>

                        </div>

                        <div style="
                            padding:30px;
                            color:#334155;
                            line-height:1.7;
                        ">

                            <h2 style="color:#1e293b;">
                                %s
                            </h2>

                            <p>Hello %s,</p>

                            <div style="
                                background:#eff6ff;
                                padding:20px;
                                border-radius:8px;
                                border-left:4px solid #2563eb;
                            ">
                                %s
                            </div>

                            <p>
                                Thank you for using
                                Smart Library.
                            </p>

                            <p>
                                Regards,<br>
                                <strong>
                                    Smart Library Team
                                </strong>
                            </p>

                        </div>

                        <div style="
                            background:#f8fafc;
                            padding:20px;
                            text-align:center;
                            color:#64748b;
                            font-size:12px;
                        ">

                            This is an automated email.
                            Please do not reply.

                        </div>

                    </div>

                </body>
                </html>
                """.formatted(
                safeSubject,
                safeName,
                safeMessage
        );
    }
}