package com.smartlibrary.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

    public String generateQrCode(String content) {

        try {

            QRCodeWriter writer = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = Map.of(

                    EncodeHintType.ERROR_CORRECTION,
                    ErrorCorrectionLevel.M,

                    EncodeHintType.MARGIN,
                    2,

                    EncodeHintType.CHARACTER_SET,
                    "UTF-8"
            );

            BitMatrix matrix = writer.encode(

                    content,

                    BarcodeFormat.QR_CODE,

                    300,

                    300,

                    hints
            );

            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(

                    matrix,

                    "PNG",

                    outputStream
            );

            String base64 = Base64
                    .getEncoder()
                    .encodeToString(
                            outputStream.toByteArray()
                    );

            return "data:image/png;base64," + base64;

        } catch (WriterException | IOException e) {

            throw new IllegalStateException(
                    "Unable to generate library QR code",
                    e
            );
        }
    }
}