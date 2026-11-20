package com.sis.service;

import com.sis.entity.AcademicTerm;
import com.sis.entity.Course;
import com.sis.entity.CourseSection;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.CourseSectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DeadlineReminderService {
    private final FeeService fees;
    private final AcademicTermRepository academicTerms;
    private final CourseSectionRepository sections;
    private final CourseRegistrationRepository registrations;
    private final NotificationService notifications;

    private final String timeZone;

    public DeadlineReminderService(
            FeeService fees,
            AcademicTermRepository academicTerms,
            CourseSectionRepository sections,
            CourseRegistrationRepository registrations,
            NotificationService notifications,
            @Value("${app.automation.time-zone:Africa/Nairobi}") String timeZone) {
        this.fees = fees;
        this.academicTerms = academicTerms;
        this.sections = sections;
        this.registrations = registrations;
        this.notifications = notifications;
        this.timeZone = timeZone;
    }

    @Transactional
    public void sendDailyReminders() {
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        remindAboutFees(today);
        remindAboutGrades(today);
    }

    private void remindAboutFees(LocalDate today) {
        for (FeeService.FeeReminderCandidate fee : fees.feeReminderCandidates(today.plusDays(7))) {
            String period = reminderPeriod(fee.dueDate(), today);
            if (period == null) continue;
            notifications.createAutomated(
                    "fee:" + fee.feeId() + ":" + fee.dueDate() + ":" + period,
                    fee.userId(),
                    "Fee deadline reminder",
                    "The fee \"" + fee.description() + "\" has an outstanding balance of "
                            + fee.balance().toPlainString() + " and is due on " + fee.dueDate() + ".",
                    NotificationCategory.PAYMENT);
        }
    }

    private void remindAboutGrades(LocalDate today) {
        for (AcademicTerm term : academicTerms
                .findByGradeSubmissionDeadlineIsNotNullAndGradeSubmissionDeadlineLessThanEqualOrderByGradeSubmissionDeadlineAsc(
                        today.plusDays(7))) {
            LocalDate deadline = term.getGradeSubmissionDeadline();
            String period = reminderPeriod(deadline, today);
            if (period == null) continue;

            Set<Long> coursesWithMissingGrades = new HashSet<>(registrations
                    .findCourseIdsWithMissingApprovedGradesByTerm(
                            term.getId(), RegistrationStatus.REGISTERED, GradeStatus.APPROVED));
            if (coursesWithMissingGrades.isEmpty()) continue;

            Map<Long, Set<Long>> notifiedTeachersByCourse = new HashMap<>();
            for (CourseSection section : sections
                    .findByAcademicTermIdAndActiveTrueOrderByCourseCourseCodeAsc(term.getId())) {
                Course course = section.getCourse();
                if (!coursesWithMissingGrades.contains(course.getId())) continue;
                Teacher teacher = course.getTeacher();
                User user = teacher == null ? null : teacher.getUser();
                if (user == null || user.getId() == null || !Boolean.TRUE.equals(user.getIsActive())) continue;
                if (!notifiedTeachersByCourse
                        .computeIfAbsent(course.getId(), ignored -> new HashSet<>()).add(user.getId())) {
                    continue;
                }
                notifications.createAutomated(
                        "grade:" + term.getId() + ":" + course.getId() + ":" + user.getId()
                                + ":" + deadline + ":" + period,
                        user.getId(),
                        "Grade submission deadline reminder",
                        "Approved grades are still missing for " + course.getCourseCode() + " in "
                                + term.getName() + ". The submission deadline is " + deadline + ".",
                        NotificationCategory.GRADE);
            }
        }
    }

    private String reminderPeriod(LocalDate dueDate, LocalDate today) {
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);
        if (daysUntilDue == 7 || daysUntilDue == 3 || daysUntilDue == 1) {
            return "before-" + daysUntilDue;
        }
        if (daysUntilDue == 0) return "due-today";
        long daysOverdue = -daysUntilDue;
        if (daysOverdue > 0 && daysOverdue % 7 == 0) {
            return "overdue-week-" + daysOverdue / 7;
        }
        return null;
    }
}
