package com.civicsense.storage;

public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException(String key) {
        super("Object not found in storage: " + key);
    }
}
