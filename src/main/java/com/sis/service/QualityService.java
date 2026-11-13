package com.sis.service;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.repository.AccreditationRecordRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ProgramOutcomeRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class QualityService {
    private final ProgramOutcomeRepository outcomes;
    private final AccreditationRecordRepository accreditations;
    private final ProgramRepository programs;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public QualityService(ProgramOutcomeRepository outcomes, AccreditationRecordRepository accreditations,
                         ProgramRepository programs, AuditLogRepository auditLogs, UserRepository users) {
        this.outcomes = outcomes;
        this.accreditations = accreditations;
        this.programs = programs;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public ProgramOutcome createOutcome(CreateProgramOutcomeRequest request) {
        ProgramOutcome outcome = new ProgramOutcome();
        outcome.setProgram(programs.findByIdWithDirectory(request.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found")));
        outcome.setCode(request.getCode());
        outcome.setDescription(request.getDescription());
        return outcomes.save(outcome);
    }

    @Transactional(readOnly = true)
    public List<ProgramOutcome> outcomes(Long programId) { return outcomes.findByProgramIdAndActiveTrueOrderByCodeAsc(programId); }

    public AccreditationRecord createAccreditation(CreateAccreditationRequest request) {
        AccreditationRecord record = new AccreditationRecord();
        record.setProgram(programs.findByIdWithDirectory(request.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found")));
        record.setAccreditingBody(request.getAccreditingBody());
        record.setCertificateReference(request.getCertificateReference());
        record.setValidFrom(request.getValidFrom());
        record.setValidUntil(request.getValidUntil());
        record.setEvidencePath(request.getEvidencePath());
        return accreditations.save(record);
    }

    @Transactional(readOnly = true)
    public List<AccreditationRecord> pendingAccreditations() { return accreditations.findByStatusOrderByValidUntilAsc(AccreditationStatus.SUBMITTED); }

    public AccreditationRecord review(Long id, ReviewAccreditationRequest request, String reviewer) {
        AccreditationRecord record = accreditations.findByIdWithProgramDirectory(id)
                .orElseThrow(() -> new IllegalArgumentException("Accreditation record not found"));
        if (record.getStatus() == AccreditationStatus.ACCREDITED || record.getStatus() == AccreditationStatus.EXPIRED) throw new IllegalStateException("Accreditation record is finalized");
        record.setStatus(request.getStatus());
        record.setReviewedBy(reviewer);
        record.setReviewedAt(LocalDateTime.now());
        AccreditationRecord saved = accreditations.save(record);
        Long userId = users.findByUsernameNormalized(reviewer)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + reviewer))
                .getId();
        auditLogs.save(new AuditLog(userId, "QUALITY_ACREDITATION_REVIEWED",
                "Accreditation record #" + saved.getId() + " status " + saved.getStatus() + " for program #" + saved.getProgram().getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AccreditationRecord> accreditations(Long programId) { return accreditations.findByProgramIdOrderByValidUntilDesc(programId); }
}