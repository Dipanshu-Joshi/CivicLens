package com.civicsense.ai;

public class InvalidAiResponseException extends RuntimeException {

    public InvalidAiResponseException(String message) {
        super(message);
    }
}
