package com.example.cloud_box.exception;

public class InternalServerException extends RuntimeException {
    public InternalServerException(String message, Exception e) {
        super(message);
    }
}
