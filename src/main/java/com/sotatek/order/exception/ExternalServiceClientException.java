package com.sotatek.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ExternalServiceClientException extends RuntimeException {
    public ExternalServiceClientException(String message) {
        super(message);
    }
}
