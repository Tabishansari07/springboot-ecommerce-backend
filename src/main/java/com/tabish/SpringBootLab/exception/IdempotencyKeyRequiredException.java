package com.tabish.SpringBootLab.exception;

public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException() {
        super("Idempotency-Key header is required");
    }
}