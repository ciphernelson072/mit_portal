package com.sis.service;

import com.sis.entity.Permission;
import com.sis.repository.PermissionRepository;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
public class PermissionCatalogInitializer {
        private static final Map<String, String> STANDARD_PERMISSIONS = Map.ofEntries(
            Map.entry("FINANCE_HOLD_MANAGE", "Create and release student financial holds"),
            Map.entry("FINANCE_FEE_MANAGE", "Create and manage student fee records"),
            Map.entry("FINANCE_PAYMENT_CONFIRM", "Confirm provider payment attempts"),
            Map.entry("FINANCE_REFUND", "Process payment refunds"),
            Map.entry("FINANCE_RECONCILIATION", "Import and review bank reconciliation entries"),
            Map.entry("FINANCE_WAIVER", "Approve fee waivers"),
            Map.entry("GRADE_APPROVAL_MANAGE", "Reserved for administrator grade-review operations; assignment alone does not authorize these actions"),
            Map.entry("EMAIL_CAMPAIGN_MANAGE", "View eligible email recipients and send bulk email campaigns"),
            Map.entry("PROCUREMENT_MANAGE", "Manage suppliers, procurement requests, and assets"),
            Map.entry("RESEARCH_REVIEW", "Review research projects and thesis submissions"),
            Map.entry("QUALITY_REVIEW", "Manage program outcomes and accreditation reviews"),
            Map.entry("HR_MANAGE", "Manage staff records and leave approvals"),
            Map.entry("LIBRARY_MANAGE", "Manage library catalog and circulation"),
            Map.entry("ACCOMMODATION_MANAGE", "Manage hostel rooms and allocations"),
            Map.entry("ADMISSIONS_REVIEW", "Review admissions and enroll accepted applicants"),
            Map.entry("REGISTRATION_MANAGE", "Manage academic course sections, curriculum, and prerequisites"),
            Map.entry("SERVICE_REQUEST_MANAGE", "Review and update student and staff service requests"),
            Map.entry("EXAM_PERMIT_MANAGE", "Review and approve or reject exam permit requests"),
            Map.entry("EXAM_RESULT_PUBLISH", "Reserved for administrator result-publication operations; assignment alone does not authorize these actions"),
            Map.entry("EXAM_MANAGE", "Manage exam scheduling, venues, and exam records"),
            Map.entry("ASSIGNMENT_MANAGE", "Create and manage class assignments and submissions"),
            Map.entry("ANNOUNCEMENT_MANAGE", "Create and manage official announcements"),
            Map.entry("ACADEMIC_OPS_MANAGE", "Manage academic operations, timetables, and class workflows"),
            Map.entry("COURSE_MATERIAL_MANAGE", "Create and manage course materials and learning resources"),
            Map.entry("ATTENDANCE_MANAGE", "Manage attendance tracking and class attendance records"),
            Map.entry("LEAVE_REQUEST", "Submit and approve leave requests and staff attendance adjustments"),
            Map.entry("PORTAL_SETTINGS_MANAGE", "Manage portal settings, audit logs, and configuration"),
            Map.entry("REPORTS_VIEW", "View institution-wide student, class, and staff reports"),
            Map.entry("REPORTS_APPROVE", "Reserved for administrator academic-report approval; assignment alone does not authorize this action")
        );

    private final PermissionRepository permissions;

    public PermissionCatalogInitializer(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    @PostConstruct
    public void initialize() {
        STANDARD_PERMISSIONS.forEach((code, description) -> permissions.findByCode(code).orElseGet(() -> {
            Permission permission = new Permission();
            permission.setCode(code);
            permission.setDescription(description);
            return permissions.save(permission);
        }));
    }
}