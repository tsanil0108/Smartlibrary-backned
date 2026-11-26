package com.smartlibrary.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class RazorpayException extends RuntimeException {

    public RazorpayException(String message) {

        super(message);
    }

    public RazorpayException(
            String message,
            Throwable cause
    ) {

        super(message, cause);
    }
}