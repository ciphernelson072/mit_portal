package com.sis;

import com.sis.security.PermissionSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionSecurityTest {
    private final PermissionSecurity permissionSecurity = new PermissionSecurity();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationSectionCatalogueRequiresStudentOrAcademicAccess() {
        for (String authority : List.of(
                "STUDENT", "ADMIN", "TEACHER",
                "PERM_REGISTRATION_MANAGE", "PERM_ACADEMIC_OPS_MANAGE")) {
            SecurityContextHolder.getContext().setAuthentication(authentication(authority));
            assertTrue(permissionSecurity.canViewRegistrationSections(), authority);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication("OTHER"));
        assertFalse(permissionSecurity.canViewRegistrationSections());
    }

    @Test
    void institutionStructureManagementRequiresAdministrativeAuthorityOrExplicitPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication("TEACHER"));
        assertFalse(permissionSecurity.canManageInstitutionStructure());

        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_ACADEMIC_OPS_MANAGE"));
        assertTrue(permissionSecurity.canManageInstitutionStructure());

        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_REGISTRATION_MANAGE"));
        assertTrue(permissionSecurity.canManageInstitutionStructure());

        SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
        assertTrue(permissionSecurity.canManageInstitutionStructure());
    }

    @Test
    void studentFeeManagementDoesNotGrantPaymentConfirmation() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_FINANCE_FEE_MANAGE"));
        assertTrue(permissionSecurity.canManageStudentFees());
        assertFalse(permissionSecurity.canConfirmPayments());
    }

    @Test
    void gradeApprovalPermissionIsSeparateFromResultPublication() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_GRADE_APPROVAL_MANAGE"));
        assertFalse(permissionSecurity.canReviewGradeApprovals());
        assertFalse(permissionSecurity.canPublishExamResults());

        SecurityContextHolder.getContext().setAuthentication(authentication("TEACHER"));
        assertFalse(permissionSecurity.canReviewGradeApprovals());

        SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
        assertTrue(permissionSecurity.canReviewGradeApprovals());
    }

    @Test
    void onlyAdminCanApproveAndPublishFinalAcademicReports() {
        for (String authority : List.of(
                "PERM_HR_MANAGE", "PERM_REPORTS_APPROVE", "PERM_GRADE_APPROVAL_MANAGE",
                "PERM_EXAM_RESULT_PUBLISH", "TEACHER")) {
            SecurityContextHolder.getContext().setAuthentication(authentication(authority));
            assertFalse(permissionSecurity.canApproveStudentReports(), authority);
            assertFalse(permissionSecurity.canPublishExamResults(), authority);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
        assertTrue(permissionSecurity.canApproveStudentReports());
        assertTrue(permissionSecurity.canPublishExamResults());
    }

    @Test
    void emailCampaignManagementRequiresItsDedicatedPermission() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_EMAIL_CAMPAIGN_MANAGE"));
        assertTrue(permissionSecurity.canManageEmailCampaigns());

        SecurityContextHolder.getContext().setAuthentication(authentication("TEACHER"));
        assertFalse(permissionSecurity.canManageEmailCampaigns());
    }

    @Test
    void accommodationManagementPermissionAuthorizesRoomCatalogueAccess() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_ACCOMMODATION_MANAGE"));
        assertTrue(permissionSecurity.canManageAccommodation());

        SecurityContextHolder.getContext().setAuthentication(authentication("TEACHER"));
        assertFalse(permissionSecurity.canManageAccommodation());
    }

    @Test
    void portalSettingsPermissionIsDistinctFromAdminRole() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_PORTAL_SETTINGS_MANAGE"));
        assertTrue(permissionSecurity.canManagePortalSettings());
        assertFalse(permissionSecurity.canManageRolePermissions());

        SecurityContextHolder.getContext().setAuthentication(authentication("TEACHER"));
        assertFalse(permissionSecurity.canManagePortalSettings());
        assertFalse(permissionSecurity.canManageRolePermissions());

        SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
        assertTrue(permissionSecurity.canManageRolePermissions());
    }

    @Test
    void privilegedPermissionNamesAreReservedFromThePermissionCatalogAdmin() {
        SecurityContextHolder.getContext().setAuthentication(authentication("PERM_PORTAL_SETTINGS_MANAGE"));
        assertFalse(permissionSecurity.canManageRolePermissions());

        SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
        assertTrue(permissionSecurity.canManageRolePermissions());
    }

    private UsernamePasswordAuthenticationToken authentication(String authority) {
        return new UsernamePasswordAuthenticationToken(
                "test-user", "n/a", AuthorityUtils.createAuthorityList(authority));
    }
}
