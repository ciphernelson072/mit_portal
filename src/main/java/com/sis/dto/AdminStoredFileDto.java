package com.sis.dto;

import java.time.Instant;

public class AdminStoredFileDto {
    private final String path;
    private final long sizeBytes;
    private final Instant lastModified;

    public AdminStoredFileDto(String path, long sizeBytes, Instant lastModified) {
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
    }

    public String getPath() {
        return path;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getLastModified() {
        return lastModified;
    }
}
