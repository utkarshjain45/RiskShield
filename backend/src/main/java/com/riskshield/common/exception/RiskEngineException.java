package com.riskshield.common.exception;

import org.springframework.http.HttpStatus;

public class RiskEngineException extends RuntimeException {

    private final HttpStatus status;

    public RiskEngineException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public RiskEngineException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
