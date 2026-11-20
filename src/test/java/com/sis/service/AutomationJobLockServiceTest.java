package com.sis.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationJobLockServiceTest {
    @Test
    void skipsAJobWhenAnotherInstanceHoldsTheDatabaseLease() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        AutomationJobLockService locks = new AutomationJobLockService(jdbcTemplate);

        assertFalse(locks.runIfAcquired("daily-deadline-reminders", () -> {
            throw new AssertionError("A locked job must not run.");
        }));

        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.contains("SET lock_token = NULL"), any(Object[].class));
    }

    @Test
    void releasesTheLeaseAfterTheJobCompletes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        AutomationJobLockService locks = new AutomationJobLockService(jdbcTemplate);
        boolean[] ran = {false};

        assertTrue(locks.runIfAcquired("daily-deadline-reminders", () -> ran[0] = true));

        assertTrue(ran[0]);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
    }
}
