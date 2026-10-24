package com.sis.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("permissionSecurity")
public class PermissionSecurity {

    public boolean canManageFinancialHolds() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_HOLD_MANAGE");
    }

    public boolean canManageStudentFees() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_FEE_MANAGE");
    }

    public boolean canConfirmPayments() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_PAYMENT_CONFIRM");
    }

    public boolean canRefundPayments() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_REFUND");
    }

    public boolean canReconcilePayments() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_RECONCILIATION");
    }

    public boolean canApproveFeeWaivers() {
        return hasAnyAuthority("ADMIN", "PERM_FINANCE_WAIVER");
    }

    public boolean canReviewAdmissions() {
        return hasAnyAuthority("ADMIN", "PERM_ADMISSIONS_REVIEW");
    }

    public boolean canManageRegistration() {
        return hasAnyAuthority("ADMIN", "PERM_REGISTRATION_MANAGE");
    }

    public boolean canViewRegistrationSections() {
        return hasAnyAuthority("STUDENT", "ADMIN", "TEACHER",
                "PERM_REGISTRATION_MANAGE", "PERM_ACADEMIC_OPS_MANAGE");
    }

    public boolean canManageExamPermits() {
        return hasAnyAuthority("ADMIN", "PERM_EXAM_PERMIT_MANAGE");
    }

    public boolean canPublishExamResults() {
        return hasAnyAuthority("ADMIN", "PERM_EXAM_RESULT_PUBLISH");
    }

    public boolean canReviewGradeApprovals() {
        return hasAnyAuthority("ADMIN", "PERM_GRADE_APPROVAL_MANAGE");
    }

    public boolean canManageEmailCampaigns() {
        return hasAnyAuthority("ADMIN", "PERM_EMAIL_CAMPAIGN_MANAGE");
    }

    public boolean canManageServiceRequests() {
        return hasAnyAuthority("ADMIN", "PERM_SERVICE_REQUEST_MANAGE");
    }

    public boolean canManageProcurement() {
        return hasAnyAuthority("ADMIN", "PERM_PROCUREMENT_MANAGE");
    }

    public boolean canManageHumanResources() {
        return hasAnyAuthority("ADMIN", "PERM_HR_MANAGE");
    }

    public boolean canManageLibrary() {
        return hasAnyAuthority("ADMIN", "PERM_LIBRARY_MANAGE");
    }

    public boolean canManageAccommodation() {
        return hasAnyAuthority("ADMIN", "PERM_ACCOMMODATION_MANAGE");
    }

    public boolean canReviewQuality() {
        return hasAnyAuthority("ADMIN", "PERM_QUALITY_REVIEW");
    }

    public boolean canReviewResearch() {
        return hasAnyAuthority("ADMIN", "PERM_RESEARCH_REVIEW");
    }

    public boolean canManageAssignments() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_ASSIGNMENT_MANAGE");
    }

    public boolean canManagePortalSettings() {
        return hasAnyAuthority("ADMIN", "PERM_PORTAL_SETTINGS_MANAGE");
    }

    public boolean canManageRolePermissions() {
        return hasAnyAuthority("ADMIN");
    }

    public boolean canManageExams() {
        return hasAnyAuthority("ADMIN", "PERM_EXAM_MANAGE");
    }

    public boolean canViewInstitutionalReports() {
        return hasAnyAuthority("ADMIN", "PERM_REPORTS_VIEW");
    }

    public boolean canApproveStudentReports() {
        return hasAnyAuthority("ADMIN", "PERM_HR_MANAGE", "PERM_REPORTS_APPROVE");
    }

    public boolean canManageAnnouncements() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_ANNOUNCEMENT_MANAGE");
    }

    public boolean canManageAcademicOperations() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_ACADEMIC_OPS_MANAGE");
    }

    public boolean canManageCourseMaterials() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_COURSE_MATERIAL_MANAGE");
    }

    public boolean canManageAttendance() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_ATTENDANCE_MANAGE");
    }

    public boolean canRequestLeave() {
        return hasAnyAuthority("ADMIN", "TEACHER", "PERM_LEAVE_REQUEST");
    }

    private boolean hasAnyAuthority(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String required : authorities) {
                if (required.equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}
