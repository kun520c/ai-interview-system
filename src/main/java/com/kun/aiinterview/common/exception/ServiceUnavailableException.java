package com.kun.aiinterview.common.exception;

/** A known refusal before work is accepted, with a safe user-facing explanation. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
