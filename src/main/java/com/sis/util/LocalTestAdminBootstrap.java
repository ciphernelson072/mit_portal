package com.sis.util;

import com.sis.entity.Role;
import com.sis.repository.UserRepository;
import com.sis.service.SchoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "sis.test-admin-bootstrap.enabled", havingValue = "true")
public class LocalTestAdminBootstrap implements ApplicationRunner {
    private final Environment environment;
    private final ConfigurableApplicationContext applicationContext;
    private final UserRepository userRepository;
    private final SchoolService schoolService;
    private final String databaseUrl;
    private final String serverAddress;
    private final String webApplicationType;
    private final boolean sampleDataEnabled;
    private final String username;
    private final String password;

    public LocalTestAdminBootstrap(Environment environment,
                                   ConfigurableApplicationContext applicationContext,
                                   UserRepository userRepository,
                                   SchoolService schoolService,
                                   @Value("${spring.datasource.url}") String databaseUrl,
                                   @Value("${server.address:127.0.0.1}") String serverAddress,
                                   @Value("${spring.main.web-application-type:servlet}") String webApplicationType,
                                   @Value("${sis.sample-data.enabled:false}") boolean sampleDataEnabled,
                                   @Value("${sis.test-admin-bootstrap.username:}") String username,
                                   @Value("${sis.test-admin-bootstrap.password:}") String password) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.userRepository = userRepository;
        this.schoolService = schoolService;
        this.databaseUrl = databaseUrl;
        this.serverAddress = serverAddress;
        this.webApplicationType = webApplicationType;
        this.sampleDataEnabled = sampleDataEnabled;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateConfiguration();
        if (userRepository.count() != 0) {
            throw new IllegalStateException("Admin bootstrap requires an empty users table.");
        }

        schoolService.createUser(username.trim(), password, Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Could not create the local test administrator."));
        if (userRepository.count() != 1
                || userRepository.countByRoleAndIsActive(Role.ADMIN, true) != 1) {
            throw new IllegalStateException("Local test administrator verification failed.");
        }

        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void validateConfiguration() {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("dev"::equals)) {
            throw new IllegalStateException("Admin bootstrap is available only with the dev profile.");
        }
        if (!databaseUrl.matches("(?i)^jdbc:mysql://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?/[A-Za-z0-9_]+_test(?:[?;].*)?$")) {
            throw new IllegalStateException("Admin bootstrap requires a local MySQL database whose name ends in _test.");
        }
        if (!"127.0.0.1".equals(serverAddress) && !"::1".equals(serverAddress)) {
            throw new IllegalStateException("Admin bootstrap must bind to loopback only.");
        }
        if (!"servlet".equalsIgnoreCase(webApplicationType)) {
            throw new IllegalStateException("Admin bootstrap requires the loopback web application to start and stop normally.");
        }
        if (sampleDataEnabled) {
            throw new IllegalStateException("Disable sample data before creating the admin-only test database.");
        }
        if (username == null || username.trim().length() < 3 || username.trim().length() > 64) {
            throw new IllegalStateException("Provide an administrator username between 3 and 64 characters.");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("Provide an administrator password of at least 12 characters.");
        }
        if (username.trim().toLowerCase(Locale.ROOT).equals(password.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("The administrator username and password must differ.");
        }
    }
}
