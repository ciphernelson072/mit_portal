package com.sis.security;

import org.springframework.stereotype.Component;

/**
 * Referenced from @PreAuthorize on FileUploadController's download/exists/info
 * endpoints. Those take a raw {@code filePath} rather than a student/teacher ID
 * directly, but FileUploadService always names files {@code {ownerId}_{uuid}.ext}
 * under a {@code students/} or {@code teachers/} directory (see
 * FileUploadService#uploadProfileImage) - so the owning ID can be recovered from the
 * path itself and checked the same way upload already is, rather than falling back to
 * "any authenticated user can fetch any file", which would let any student browse
 * every other student's and teacher's profile photo by guessing/enumerating paths.
 */
@Component("fileAccessService")
public class FileAccessService {

    private final StudentAccessService studentAccessService;
    private final TeacherAccessService teacherAccessService;

    public FileAccessService(StudentAccessService studentAccessService, TeacherAccessService teacherAccessService) {
        this.studentAccessService = studentAccessService;
        this.teacherAccessService = teacherAccessService;
    }

    public boolean isOwnFile(String filePath) {
        if (filePath == null) return false;

        String normalized = filePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.contains("//")
                || !(normalized.startsWith("students/") || normalized.startsWith("teachers/"))) {
            return false;
        }

        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()))) {
            return true;
        }

        Long ownerId = extractOwnerId(normalized);
        if (ownerId == null) return false;

        if (normalized.startsWith("students/")) {
            return studentAccessService.isOwnStudent(ownerId) || teacherAccessService.teachesStudent(ownerId);
        }
        if (normalized.startsWith("teachers/")) {
            return teacherAccessService.isOwnTeacher(ownerId);
        }
        return false;
    }

    private Long extractOwnerId(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        String filename = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        int underscore = filename.indexOf('_');
        if (underscore <= 0) return null;
        try {
            return Long.parseLong(filename.substring(0, underscore));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
