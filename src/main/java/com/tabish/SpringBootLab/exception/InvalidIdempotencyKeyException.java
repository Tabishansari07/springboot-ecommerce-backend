package com.tabish.SpringBootLab.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
