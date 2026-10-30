package com.sis.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component("permissionSecurity")
public class PermissionSecurity {

    public boolean canManageFinancialHolds() {
        return hasAnyAuthority("ADMIN", "FINANCE_HOLD_MANAGE", "PERM_FINANCE_HOLD_MANAGE");
    }

    public boolean canManageStudentFees() {
        return hasAnyAuthority("ADMIN", "FINANCE_FEE_MANAGE", "PERM_FINANCE_FEE_MANAGE");
    }

    public boolean canConfirmPayments() {
        return hasAnyAuthority("ADMIN", "FINANCE_PAYMENT_CONFIRM", "PERM_FINANCE_PAYMENT_CONFIRM");
    }

    public boolean canRefundPayments() {
        return hasAnyAuthority("ADMIN", "FINANCE_REFUND", "PERM_FINANCE_REFUND");
    }

    public boolean canReconcilePayments() {
        return hasAnyAuthority("ADMIN", "FINANCE_RECONCILIATION", "PERM_FINANCE_RECONCILIATION");
    }

    public boolean canApproveFeeWaivers() {
        return hasAnyAuthority("ADMIN", "FINANCE_WAIVER", "PERM_FINANCE_WAIVER");
    }

    public boolean canReviewAdmissions() {
        return hasAnyAuthority("ADMIN", "ADMISSIONS_REVIEW", "PERM_ADMISSIONS_REVIEW");
    }

    public boolean canManageRegistration() {
        return hasAnyAuthority("ADMIN", "REGISTRATION_MANAGE", "PERM_REGISTRATION_MANAGE");
    }

    public boolean canManageInstitutionStructure() {
        return hasAnyAuthority("ADMIN", "ACADEMIC_OPS_MANAGE", "PERM_ACADEMIC_OPS_MANAGE",
                "REGISTRATION_MANAGE", "PERM_REGISTRATION_MANAGE");
    }

    public boolean canViewRegistrationSections() {
        return hasAnyAuthority("STUDENT", "ADMIN", "TEACHER",
                "REGISTRATION_MANAGE", "ACADEMIC_OPS_MANAGE",
                "PERM_REGISTRATION_MANAGE", "PERM_ACADEMIC_OPS_MANAGE");
    }

    public boolean canManageExamPermits() {
        return hasAnyAuthority("ADMIN", "EXAM_PERMIT_MANAGE", "PERM_EXAM_PERMIT_MANAGE");
    }

    public boolean canPublishExamResults() {
        return hasAnyAuthority("ADMIN");
    }

    public boolean canReviewGradeApprovals() {
        return hasAnyAuthority("ADMIN");
    }

    public boolean canManageEmailCampaigns() {
        return hasAnyAuthority("ADMIN", "EMAIL_CAMPAIGN_MANAGE", "PERM_EMAIL_CAMPAIGN_MANAGE");
    }

    public boolean canManageServiceRequests() {
        return hasAnyAuthority("ADMIN", "SERVICE_REQUEST_MANAGE", "PERM_SERVICE_REQUEST_MANAGE");
    }

    public boolean canManageProcurement() {
        return hasAnyAuthority("ADMIN", "PROCUREMENT_MANAGE", "PERM_PROCUREMENT_MANAGE");
    }

    public boolean canManageHumanResources() {
        return hasAnyAuthority("ADMIN", "HR_MANAGE", "PERM_HR_MANAGE");
    }

    public boolean canManageLibrary() {
        return hasAnyAuthority("ADMIN", "LIBRARY_MANAGE", "PERM_LIBRARY_MANAGE");
    }

    public boolean canManageAccommodation() {
        return hasAnyAuthority("ADMIN", "ACCOMMODATION_MANAGE", "PERM_ACCOMMODATION_MANAGE");
    }

    public boolean canReviewQuality() {
        return hasAnyAuthority("ADMIN", "QUALITY_REVIEW", "PERM_QUALITY_REVIEW");
    }

    public boolean canReviewResearch() {
        return hasAnyAuthority("ADMIN", "RESEARCH_REVIEW", "PERM_RESEARCH_REVIEW");
    }

    public boolean canManageAssignments() {
        return hasAnyAuthority("ADMIN", "TEACHER", "ASSIGNMENT_MANAGE", "PERM_ASSIGNMENT_MANAGE");
    }

    public boolean canManagePortalSettings() {
        return hasAnyAuthority("ADMIN", "PORTAL_SETTINGS_MANAGE", "PERM_PORTAL_SETTINGS_MANAGE");
    }

    public boolean canManageRolePermissions() {
        return hasAnyAuthority("ADMIN");
    }

    public boolean canManageExams() {
        return hasAnyAuthority("ADMIN", "EXAM_MANAGE", "PERM_EXAM_MANAGE");
    }

    public boolean canViewInstitutionalReports() {
        return hasAnyAuthority("ADMIN", "REPORTS_VIEW", "PERM_REPORTS_VIEW");
    }

    public boolean canApproveStudentReports() {
        return hasAnyAuthority("ADMIN");
    }

    public boolean canManageAnnouncements() {
        return hasAnyAuthority("ADMIN", "TEACHER", "ANNOUNCEMENT_MANAGE", "PERM_ANNOUNCEMENT_MANAGE");
    }

    public boolean canManageAcademicOperations() {
        return hasAnyAuthority("ADMIN", "TEACHER", "ACADEMIC_OPS_MANAGE", "PERM_ACADEMIC_OPS_MANAGE");
    }

    public boolean canManageCourseMaterials() {
        return hasAnyAuthority("ADMIN", "TEACHER", "COURSE_MATERIAL_MANAGE", "PERM_COURSE_MATERIAL_MANAGE");
    }

    public boolean canManageAttendance() {
        return hasAnyAuthority("ADMIN", "TEACHER", "ATTENDANCE_MANAGE", "PERM_ATTENDANCE_MANAGE");
    }

    public boolean canRequestLeave() {
        return hasAnyAuthority("ADMIN", "TEACHER", "LEAVE_REQUEST", "PERM_LEAVE_REQUEST");
    }

    private boolean hasAnyAuthority(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String currentAuthority = authority.getAuthority();
            if (currentAuthority == null) {
                continue;
            }
            String currentNormalized = normalizeAuthority(currentAuthority);
            for (String required : authorities) {
                if (required != null && currentNormalized.equals(normalizeAuthority(required))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeAuthority(String authority) {
        if (authority == null) {
            return "";
        }
        String value = authority.trim();
        if (value.startsWith("PERM_")) {
            value = value.substring("PERM_".length());
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
