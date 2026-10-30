package com.sis.service;

import com.sis.dto.CreateLeaveRequest;
import com.sis.dto.CreateStaffRequest;
import com.sis.dto.ReviewLeaveRequest;
import com.sis.entity.*;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.LeaveRequestRepository;
import com.sis.repository.StaffRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class HrService {
    private final StaffRepository staff;
    private final LeaveRequestRepository leaves;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;

    public HrService(StaffRepository staff, LeaveRequestRepository leaves, UserRepository users, AuditLogRepository auditLogs) {
        this.staff = staff;
        this.leaves = leaves;
        this.users = users;
        this.auditLogs = auditLogs;
    }

    public Staff createStaff(CreateStaffRequest request) {
        User employeeUser = users.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!Boolean.TRUE.equals(employeeUser.getIsActive())) {
            throw new IllegalArgumentException("An active account is required for an HR staff profile");
        }
        if (employeeUser.getRole() != Role.ADMIN && employeeUser.getRole() != Role.TEACHER) {
            throw new IllegalArgumentException("HR staff profiles can only be linked to administrator or teacher accounts");
        }
        if (staff.existsByUser_Id(employeeUser.getId())) {
            throw new IllegalArgumentException("This account already has an HR staff profile");
        }
        String employeeNumber = request.getEmployeeNumber().trim();
        if (staff.existsByEmployeeNumber(employeeNumber)) {
            throw new IllegalArgumentException("Employee number is already in use");
        }
        Staff item = new Staff();
        item.setUser(employeeUser);
        item.setEmployeeNumber(employeeNumber);
        item.setFullName(request.getFullName().trim());
        item.setTitle(request.getTitle().trim());
        item.setDepartmentId(request.getDepartmentId());
        item.setHireDate(request.getHireDate());
        return staff.save(item);
    }

    @Transactional(readOnly = true)
    public List<Staff> listStaff() { return staff.findAll(); }

    public LeaveRequest requestLeave(String username, CreateLeaveRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) throw new IllegalArgumentException("Leave end date must not precede start date");
        Staff employee = staff.findByUserUsername(username).filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Active staff profile not found"));
        boolean overlap = leaves.findByStaffIdAndStatus(employee.getId(), LeaveRequestStatus.PENDING).stream()
                .anyMatch(item -> !request.getEndDate().isBefore(item.getStartDate()) && !request.getStartDate().isAfter(item.getEndDate()));
        if (overlap) throw new IllegalStateException("An overlapping leave request is already pending");
        LeaveRequest leave = new LeaveRequest();
        leave.setStaff(employee);
        leave.setStartDate(request.getStartDate());
        leave.setEndDate(request.getEndDate());
        leave.setReason(request.getReason());
        LeaveRequest saved = leaves.save(leave);
        return leaves.findByIdWithStaffUser(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved leave request could not be reloaded"));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> leavesForStaff(String username) {
        Staff employee = staff.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Staff profile not found"));
        return leaves.findByStaffIdOrderByCreatedAtDesc(employee.getId());
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> pendingLeaves() { return leaves.findByStatusOrderByCreatedAtAsc(LeaveRequestStatus.PENDING); }

    public LeaveRequest reviewLeave(Long id, ReviewLeaveRequest request, String reviewer) {
        if (request.getStatus() != LeaveRequestStatus.APPROVED && request.getStatus() != LeaveRequestStatus.REJECTED) {
            throw new IllegalArgumentException("Leave review must be APPROVED or REJECTED");
        }
        LeaveRequest leave = leaves.findByIdWithStaffUser(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found"));
        if (leave.getStatus() != LeaveRequestStatus.PENDING) throw new IllegalStateException("Leave request is already finalized");
        leave.setStatus(request.getStatus());
        leave.setReviewedBy(reviewer);
        leave.setReviewedAt(LocalDateTime.now());
        LeaveRequest saved = leaves.save(leave);
        Long userId = users.findByUsernameNormalized(reviewer)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + reviewer))
                .getId();
        auditLogs.save(new AuditLog(userId, "HR_LEAVE_REVIEWED",
                "Leave request #" + saved.getId() + " reviewed by " + reviewer + " status " + saved.getStatus()));
        return saved;
    }
}