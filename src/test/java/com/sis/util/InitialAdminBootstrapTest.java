package com.sis.util;

import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialAdminBootstrapTest {
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Test
    void createsAnAdminOnlyForAnUnclaimedEmptyDatabase() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap(userRepository, passwordEncoder, jdbcTemplate, "first-admin",
                "Admin@example.edu", PASSWORD).run(mock(ApplicationArguments.class));

        verify(jdbcTemplate).update(anyString(), eq("initial-admin"));
        verify(passwordEncoder).encode(PASSWORD);
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertEquals("first-admin", captor.getValue().getUsername());
        assertEquals("admin@example.edu", captor.getValue().getEmail());
        assertEquals("bcrypt-hash", captor.getValue().getPassword());
        assertEquals(Role.ADMIN, captor.getValue().getRole());
    }

    @Test
    void doesNotCreateAnAdminAfterTheOneTimeClaimHasBeenUsed() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(0);

        bootstrap(userRepository, passwordEncoder, jdbcTemplate, "first-admin",
                "admin@example.edu", PASSWORD).run(mock(ApplicationArguments.class));

        verify(userRepository, never()).count();
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void consumesTheOneTimeClaimWithoutCreatingAnAdminWhenAccountsAlreadyExist() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), anyString())).thenReturn(1);
        when(userRepository.count()).thenReturn(1L);

        bootstrap(userRepository, passwordEncoder, jdbcTemplate, "first-admin",
                "admin@example.edu", PASSWORD).run(mock(ApplicationArguments.class));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void rejectsWeakPasswordsBeforeClaimingBootstrapState() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        InitialAdminBootstrap bootstrap = bootstrap(mock(UserRepository.class), mock(PasswordEncoder.class),
                jdbcTemplate, "first-admin", "admin@example.edu", "short");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> bootstrap.run(mock(ApplicationArguments.class)));
        assertTrue(error.getMessage().contains("password"));
        verify(jdbcTemplate, never()).update(anyString(), anyString());
    }

    private InitialAdminBootstrap bootstrap(UserRepository userRepository,
                                            PasswordEncoder passwordEncoder,
                                            JdbcTemplate jdbcTemplate,
                                            String username,
                                            String email,
                                            String password) {
        return new InitialAdminBootstrap(userRepository, passwordEncoder, jdbcTemplate,
                username, email, password);
    }
}
