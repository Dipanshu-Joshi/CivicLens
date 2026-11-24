package com.civicsense.storage;

/** Provider failure. Callers must surface this honestly, never as success. */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageUnavailableException(String message) {
        super(message);
    }
}
