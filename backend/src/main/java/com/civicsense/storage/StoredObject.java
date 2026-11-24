package com.civicsense.storage;

/** Object bytes plus provider-reported metadata, read during upload confirmation. */
public record StoredObject(byte[] bytes, String contentType, long size) {
}
