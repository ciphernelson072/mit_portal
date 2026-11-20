package com.sis.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AutomationJobLockService {
    private static final String ACQUIRE_SQL = """
            UPDATE automation_job_locks
            SET lock_token = ?, locked_until = TIMESTAMPADD(MINUTE, 60, CURRENT_TIMESTAMP)
            WHERE job_key = ? AND locked_until <= CURRENT_TIMESTAMP
            """;
    private static final String RELEASE_SQL = """
            UPDATE automation_job_locks
            SET lock_token = NULL, locked_until = CURRENT_TIMESTAMP
            WHERE job_key = ? AND lock_token = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AutomationJobLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean runIfAcquired(String jobKey, Runnable job) {
        String token = UUID.randomUUID().toString();
        if (jdbcTemplate.update(ACQUIRE_SQL, token, jobKey) != 1) {
            return false;
        }
        try {
            job.run();
        } finally {
            jdbcTemplate.update(RELEASE_SQL, jobKey, token);
        }
        return true;
    }
}
