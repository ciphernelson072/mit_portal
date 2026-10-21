package com.sis.dto;

import java.util.List;

public class AdminFileListingDto {
    private final List<AdminStoredFileDto> files;
    private final boolean truncated;

    public AdminFileListingDto(List<AdminStoredFileDto> files, boolean truncated) {
        this.files = List.copyOf(files);
        this.truncated = truncated;
    }

    public List<AdminStoredFileDto> getFiles() {
        return files;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
