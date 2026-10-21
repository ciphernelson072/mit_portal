package com.sis.util;

import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Provisions the first administrator only when explicitly enabled for an empty production database. */
@Component
@Profile("prod")
@ConditionalOnProperty(name = "sis.initial-admin-bootstrap.enabled", havingValue = "true")
public class InitialAdminBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private static final String BOOTSTRAP_KEY = "initial-admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final String username;
    private final String email;
    private final String password;

    public InitialAdminBootstrap(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 JdbcTemplate jdbcTemplate,
                                 @Value("${sis.initial-admin-bootstrap.username:}") String username,
                                 @Value("${sis.initial-admin-bootstrap.email:}") String email,
                                 @Value("${sis.initial-admin-bootstrap.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();

        int claimed = jdbcTemplate.update(
                "UPDATE deployment_bootstrap_state SET completed_at = CURRENT_TIMESTAMP "
                        + "WHERE bootstrap_key = ? AND completed_at IS NULL",
                BOOTSTRAP_KEY);
        if (claimed == 0) {
            logger.info("Initial administrator bootstrap has already been completed; no account was created.");
            return;
        }
        if (claimed != 1) {
            throw new IllegalStateException("Initial administrator bootstrap state is invalid.");
        }

        if (userRepository.count() != 0) {
            logger.info("Initial administrator bootstrap was skipped because the database already contains accounts.");
            return;
        }

        User administrator = new User(username.trim(),
                passwordEncoder.encode(password), Role.ADMIN);
        administrator.setEmail(email.trim().toLowerCase(Locale.ROOT));
        userRepository.saveAndFlush(administrator);
        logger.info("Initial administrator account created. Remove the bootstrap credentials and disable "
                + "INITIAL_ADMIN_BOOTSTRAP_ENABLED before the next deployment.");
    }

    private void validateConfiguration() {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9._@+-]{3,64}")) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_USERNAME must contain 3 to 64 letters, digits, or . _ @ + - characters.");
        }
        if (email == null || !email.trim().matches("(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$")) {
            throw new IllegalStateException("INITIAL_ADMIN_EMAIL must be a valid email address.");
        }
        int passwordLength = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordLength < 12 || passwordLength > 72) {
            throw new IllegalStateException(
                    "INITIAL_ADMIN_PASSWORD must contain between 12 and 72 UTF-8 bytes.");
        }
        if (normalizedUsername.equalsIgnoreCase(password)) {
            throw new IllegalStateException("The initial administrator username and password must differ.");
        }
    }
}
