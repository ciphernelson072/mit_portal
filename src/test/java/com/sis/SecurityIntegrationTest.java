package com.sis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sis.controller.AuthController;
import com.sis.dto.AssignPermissionRequest;
import com.sis.dto.CreatePermissionRequest;
import com.sis.dto.MfaDisableRequest;
import com.sis.dto.MfaSetupRequest;
import com.sis.dto.MfaSetupResponse;
import com.sis.dto.MfaVerifyRequest;
import com.sis.dto.UpdateUserRequest;
import com.sis.entity.AuditLog;
import com.sis.entity.Campus;
import com.sis.entity.Course;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.Exam;
import com.sis.entity.ExamPermit;
import com.sis.entity.ExamPermitStatus;
import com.sis.entity.Permission;
import com.sis.entity.Fee;
import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentAttemptStatus;
import com.sis.entity.PaymentChannel;
import com.sis.entity.ProcurementRequest;
import com.sis.entity.Program;
import com.sis.entity.Role;
import com.sis.entity.RolePermission;
import com.sis.entity.ServiceRequest;
import com.sis.entity.Staff;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.entity.LeaveRequest;
import com.sis.entity.AccreditationRecord;
import com.sis.entity.AccreditationStatus;
import com.sis.entity.PasswordResetToken;
import com.sis.entity.EmailVerificationToken;
import com.sis.entity.RefreshToken;
import com.sis.exception.EmailDeliveryException;
import com.sis.repository.AccreditationRecordRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.CampusRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.ExamPermitRepository;
import com.sis.repository.ExamRepository;
import com.sis.repository.FacultyRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.LeaveRequestRepository;
import com.sis.repository.PaymentAttemptRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PasswordResetTokenRepository;
import com.sis.repository.EmailVerificationTokenRepository;
import com.sis.repository.ProcurementRequestRepository;
import com.sis.repository.PermissionRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.RolePermissionRepository;
import com.sis.repository.ServiceRequestRepository;
import com.sis.repository.ServiceRequestEventRepository;
import com.sis.repository.StaffRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import com.sis.service.QualityService;
import com.sis.service.ServiceRequestService;
import com.sis.service.PermissionService;
import com.sis.service.AdminService;
import com.sis.service.AuthenticationService;
import com.sis.service.CustomUserDetailsService;
import com.sis.service.EmailService;
import com.sis.service.MfaSecretCipher;
import com.sis.service.RefreshTokenService;
import com.sis.security.OriginValidationFilter;
import com.sis.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * End-to-end security tests against the real (H2, per src/test/resources/
 * application.properties) application context - not a mocked slice. This exercises
 * the actual SecurityConfig, the actual JwtAuthenticationFilter, the actual
 * RateLimitFilter, and the actual cookie-based login flow, rather than testing
 * against assumptions about how those pieces are wired together.
 * <p>
 * Credentials used here (admin/admin123, student1/student123) are the same demo
 * accounts SampleDataLoader seeds - see README.md's Demo Credentials section.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SecurityIntegrationTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private MfaSecretCipher mfaSecretCipher;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamPermitRepository examPermitRepository;

    @Autowired
    private AccreditationRecordRepository accreditationRecordRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FeeRepository feeRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private ServiceRequestEventRepository serviceRequestEventRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ServiceRequestService serviceRequestService;

    @Autowired
    private QualityService qualityService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AdminService adminService;

    @MockitoSpyBean
    private AuthenticationService authenticationService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private RoleHierarchy roleHierarchy;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @MockitoSpyBean
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void publicCourseCatalogueIsViewableWithoutOpeningAdminCourseManagement() {
        ResponseEntity<String> page = restTemplate.getForEntity("/course-catalog.html", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("Course catalogue"));

        ResponseEntity<String> catalogue = restTemplate.getForEntity("/api/public/courses", String.class);
        assertEquals(HttpStatus.OK, catalogue.getStatusCode());

        ResponseEntity<String> adminCourses = restTemplate.getForEntity("/api/admin/courses", String.class);
        assertTrue(adminCourses.getStatusCode() == HttpStatus.UNAUTHORIZED
                || adminCourses.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    void privilegedLoginIsRestrictedToMfaEnrollmentUntilVerification() {
        User user = userRepository.findByUsername("admin").orElseThrow();
        boolean originalMfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
        String originalMfaSecret = user.getMfaSecret();
        int originalCredentialVersion = user.getCredentialVersion();
        boolean originalPasswordChangeRequired = user.isPasswordChangeRequired();
        boolean originalControllerMfaEnabled = (boolean) ReflectionTestUtils.getField(authController, "mfaEnabled");
        boolean originalControllerMfaRequired =
                (boolean) ReflectionTestUtils.getField(authController, "mfaRequiredForPrivileged");
        boolean originalFilterMfaRequired =
                (boolean) ReflectionTestUtils.getField(jwtAuthenticationFilter, "mfaRequiredForPrivileged");
        String originalEncryptionKey = (String) ReflectionTestUtils.getField(mfaSecretCipher, "encodedKey");

        ReflectionTestUtils.setField(authController, "mfaEnabled", true);
        ReflectionTestUtils.setField(authController, "mfaRequiredForPrivileged", true);
        ReflectionTestUtils.setField(jwtAuthenticationFilter, "mfaRequiredForPrivileged", true);
        ReflectionTestUtils.setField(mfaSecretCipher, "encodedKey",
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setPasswordChangeRequired(false);
        userRepository.saveAndFlush(user);

        try {
            HttpHeaders loginHeaders = new HttpHeaders();
            loginHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> loginRequest =
                    new HttpEntity<>(Map.of("username", "admin", "password", "admin123"), loginHeaders);
            ResponseEntity<Map> pendingLogin =
                    restTemplate.postForEntity("/api/auth/login", loginRequest, Map.class);

            assertEquals(HttpStatus.FORBIDDEN, pendingLogin.getStatusCode());
            assertTrue(Boolean.TRUE.equals(pendingLogin.getBody().get("mfaEnrollmentRequired")));
            assertTrue(pendingLogin.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                    .anyMatch(cookie -> cookie.startsWith("refresh_token=") && cookie.contains("Max-Age=0")));
            String pendingAccessToken = accessTokenCookie(pendingLogin);
            HttpHeaders pendingHeaders = new HttpHeaders();
            pendingHeaders.set(HttpHeaders.COOKIE, "access_token=" + pendingAccessToken);

            ResponseEntity<Boolean> mfaStatus = restTemplate.exchange(
                    "/api/auth/mfa/status", HttpMethod.GET, new HttpEntity<>(pendingHeaders), Boolean.class);
            assertEquals(HttpStatus.OK, mfaStatus.getStatusCode());
            assertFalse(Boolean.TRUE.equals(mfaStatus.getBody()));

            ResponseEntity<String> deniedPortalApi = restTemplate.exchange(
                    "/api/auth/authorities", HttpMethod.GET, new HttpEntity<>(pendingHeaders), String.class);
            assertEquals(HttpStatus.FORBIDDEN, deniedPortalApi.getStatusCode());

            ResponseEntity<String> securityPage = restTemplate.exchange(
                    "/account-security.html", HttpMethod.GET, new HttpEntity<>(pendingHeaders), String.class);
            assertEquals(HttpStatus.OK, securityPage.getStatusCode());
            assertTrue(securityPage.getBody().contains("Authenticator two-factor authentication"));

            MfaSetupRequest setupRequest = new MfaSetupRequest();
            setupRequest.setCurrentPassword("admin123");
            ResponseEntity<MfaSetupResponse> setupResponse = restTemplate.exchange(
                    "/api/auth/mfa/setup", HttpMethod.POST,
                    new HttpEntity<>(setupRequest, pendingHeaders), MfaSetupResponse.class);
            assertEquals(HttpStatus.OK, setupResponse.getStatusCode());
            String secret = setupResponse.getBody().getSecret();

            String otp = ReflectionTestUtils.invokeMethod(authController, "generateTotp",
                    secret, java.time.Instant.now().getEpochSecond() / 30L);
            MfaVerifyRequest verifyRequest = new MfaVerifyRequest();
            verifyRequest.setCurrentPassword("admin123");
            verifyRequest.setOtp(otp);
            ResponseEntity<Void> verifyResponse = restTemplate.exchange(
                    "/api/auth/mfa/verify", HttpMethod.POST,
                    new HttpEntity<>(verifyRequest, pendingHeaders), Void.class);
            assertEquals(HttpStatus.OK, verifyResponse.getStatusCode());

            ResponseEntity<String> staleEnrollmentApi = restTemplate.exchange(
                    "/api/auth/authorities", HttpMethod.GET, new HttpEntity<>(pendingHeaders), String.class);
            assertEquals(HttpStatus.FORBIDDEN, staleEnrollmentApi.getStatusCode());

            otp = ReflectionTestUtils.invokeMethod(authController, "generateTotp",
                    secret, java.time.Instant.now().getEpochSecond() / 30L);
            HttpEntity<Map<String, String>> verifiedLoginRequest = new HttpEntity<>(
                    Map.of("username", "admin", "password", "admin123", "otp", otp), loginHeaders);
            ResponseEntity<Map> verifiedLogin =
                    restTemplate.postForEntity("/api/auth/login", verifiedLoginRequest, Map.class);
            assertEquals(HttpStatus.OK, verifiedLogin.getStatusCode());
            HttpHeaders verifiedHeaders = new HttpHeaders();
            verifiedHeaders.set(HttpHeaders.COOKIE, "access_token=" + accessTokenCookie(verifiedLogin));

            ResponseEntity<String> privilegedApi = restTemplate.exchange(
                    "/api/auth/authorities", HttpMethod.GET, new HttpEntity<>(verifiedHeaders), String.class);
            assertEquals(HttpStatus.OK, privilegedApi.getStatusCode());
            assertTrue(privilegedApi.getBody().contains("ADMIN"));

            HttpHeaders refreshHeaders = new HttpHeaders();
            refreshHeaders.set(HttpHeaders.COOKIE,
                    "refresh_token=" + cookieValue(verifiedLogin, "refresh_token"));
            ResponseEntity<Map> refreshed = restTemplate.exchange(
                    "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshHeaders), Map.class);
            assertEquals(HttpStatus.OK, refreshed.getStatusCode());
            HttpHeaders refreshedAccessHeaders = new HttpHeaders();
            refreshedAccessHeaders.set(HttpHeaders.COOKIE,
                    "access_token=" + accessTokenCookie(refreshed));
            ResponseEntity<String> refreshedPrivilegedApi = restTemplate.exchange(
                    "/api/auth/authorities", HttpMethod.GET,
                    new HttpEntity<>(refreshedAccessHeaders), String.class);
            assertEquals(HttpStatus.OK, refreshedPrivilegedApi.getStatusCode());

            String unverifiedRefreshToken = refreshTokenService.issue(user, false);
            HttpHeaders unverifiedRefreshHeaders = new HttpHeaders();
            unverifiedRefreshHeaders.set(HttpHeaders.COOKIE,
                    "refresh_token=" + unverifiedRefreshToken);
            ResponseEntity<String> unverifiedRefresh = restTemplate.exchange(
                    "/api/auth/refresh", HttpMethod.POST,
                    new HttpEntity<>(unverifiedRefreshHeaders), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, unverifiedRefresh.getStatusCode());

            MfaDisableRequest disableRequest = new MfaDisableRequest();
            disableRequest.setCurrentPassword("admin123");
            disableRequest.setOtp(otp);
            ResponseEntity<Void> disableResponse = restTemplate.exchange(
                    "/api/auth/mfa/disable", HttpMethod.POST,
                    new HttpEntity<>(disableRequest, refreshedAccessHeaders), Void.class);
            assertEquals(HttpStatus.FORBIDDEN, disableResponse.getStatusCode());
        } finally {
            refreshTokenService.revokeAllForUser(user.getId());
            user.setMfaEnabled(originalMfaEnabled);
            user.setMfaSecret(originalMfaSecret);
            user.setCredentialVersion(originalCredentialVersion);
            user.setPasswordChangeRequired(originalPasswordChangeRequired);
            userRepository.saveAndFlush(user);
            ReflectionTestUtils.setField(authController, "mfaEnabled", originalControllerMfaEnabled);
            ReflectionTestUtils.setField(authController, "mfaRequiredForPrivileged", originalControllerMfaRequired);
            ReflectionTestUtils.setField(jwtAuthenticationFilter, "mfaRequiredForPrivileged",
                    originalFilterMfaRequired);
            ReflectionTestUtils.setField(mfaSecretCipher, "encodedKey", originalEncryptionKey);
        }
    }

    private String accessTokenCookie(ResponseEntity<?> response) {
        return cookieValue(response, "access_token");
    }

    private String cookieValue(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1, cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Response did not set a " + name + " cookie"));
    }

    @Test
    void adminTeacherAndStudentRolesFollowTheDeclaredHierarchy() {
        var adminAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")));
        assertTrue(adminAuthorities.stream().anyMatch(authority -> "TEACHER".equals(authority.getAuthority())));
        assertTrue(adminAuthorities.stream().anyMatch(authority -> "STUDENT".equals(authority.getAuthority())));

        var teacherAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("TEACHER")));
        assertFalse(teacherAuthorities.stream().anyMatch(authority -> "ADMIN".equals(authority.getAuthority())));
        assertFalse(teacherAuthorities.stream().anyMatch(authority -> "STUDENT".equals(authority.getAuthority())));

        var studentAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("STUDENT")));
        assertFalse(studentAuthorities.stream().anyMatch(authority -> "ADMIN".equals(authority.getAuthority())));
        assertFalse(studentAuthorities.stream().anyMatch(authority -> "TEACHER".equals(authority.getAuthority())));

        var studentDetails = customUserDetailsService.loadUserByUsername("student1");
        assertTrue(studentDetails.getAuthorities().stream().anyMatch(authority -> "STUDENT".equals(authority.getAuthority())));

        var teacherDetails = customUserDetailsService.loadUserByUsername("teacher1");
        assertTrue(teacherDetails.getAuthorities().stream().anyMatch(authority -> "TEACHER".equals(authority.getAuthority())));

        var adminDetails = customUserDetailsService.loadUserByUsername("admin");
        assertTrue(adminDetails.getAuthorities().stream().anyMatch(authority -> "ADMIN".equals(authority.getAuthority())));
        assertFalse(adminDetails.getAuthorities().stream().anyMatch(authority -> "PASSWORD_CHANGE_REQUIRED".equals(authority.getAuthority())));

        assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/academic/student/exam-results", HttpMethod.GET, null)
                        .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("student1", "student123", "/api/teacher/my-courses", HttpMethod.GET, null)
                        .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/students", HttpMethod.GET, null)
                        .getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                exchangeAs("admin", "admin123", "/api/academic/student/exam-results", HttpMethod.GET, null)
                        .getStatusCode(),
                "Admin inherits supervisory access but has no student profile of its own");

        assertEquals(HttpStatus.OK,
                exchangeAs("student1", "student123", "/dashboard.html", HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/teacher-dashboard.html", HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.OK,
                exchangeAs("admin", "admin123", "/teacher-dashboard.html", HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.OK,
                exchangeAs("admin", "admin123", "/admin-dashboard.html", HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.OK,
                exchangeAs("student1", "student123", "/announcements.html", HttpMethod.GET, null).getStatusCode());

        assertPageDeniedFor("teacher1", "teacher123", "/dashboard.html");
        assertPageDeniedFor("student1", "student123", "/teacher-dashboard.html");
        assertPageDeniedFor("teacher1", "teacher123", "/admin-dashboard.html");
        assertPageDeniedFor("student1", "student123", "/admin-dashboard.html");
        assertPageDeniedFor("admin", "admin123", "/dashboard.html");
        assertPageDeniedFor("teacher1", "teacher123", "/announcements.html");
        assertPageDeniedFor("admin", "admin123", "/announcements.html");
    }

    @Test
    void teacherHomeDepartmentDirectoryIsAdminOnlyAndExcludesInactiveAncestors() {
        String suffix = Long.toString(System.nanoTime());
        Campus activeCampus = new Campus();
        activeCampus.setCode("DIR-C-" + suffix);
        activeCampus.setName("Directory Active Campus " + suffix);
        activeCampus = campusRepository.saveAndFlush(activeCampus);

        Faculty activeFaculty = new Faculty();
        activeFaculty.setCode("DIR-F-" + suffix);
        activeFaculty.setName("Directory Active Faculty " + suffix);
        activeFaculty.setCampus(activeCampus);
        activeFaculty = facultyRepository.saveAndFlush(activeFaculty);

        Department activeDepartment = new Department();
        activeDepartment.setCode("DIR-D-" + suffix);
        activeDepartment.setName("Directory Active Department " + suffix);
        activeDepartment.setFaculty(activeFaculty);
        departmentRepository.saveAndFlush(activeDepartment);

        Campus inactiveCampus = new Campus();
        inactiveCampus.setCode("DIR-XC-" + suffix);
        inactiveCampus.setName("Directory Inactive Campus " + suffix);
        inactiveCampus.setActive(false);
        inactiveCampus = campusRepository.saveAndFlush(inactiveCampus);

        Faculty inactiveFaculty = new Faculty();
        inactiveFaculty.setCode("DIR-XF-" + suffix);
        inactiveFaculty.setName("Directory Inactive Faculty " + suffix);
        inactiveFaculty.setCampus(inactiveCampus);
        inactiveFaculty = facultyRepository.saveAndFlush(inactiveFaculty);

        Department inactiveDepartment = new Department();
        inactiveDepartment.setCode("DIR-XD-" + suffix);
        inactiveDepartment.setName("Directory Inactive Department " + suffix);
        inactiveDepartment.setFaculty(inactiveFaculty);
        departmentRepository.saveAndFlush(inactiveDepartment);

        ResponseEntity<String> adminResponse = exchangeAs("admin", "admin123",
                "/api/admin/institution/departments/active", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
        assertTrue(adminResponse.getBody().contains(activeDepartment.getCode()));
        assertFalse(adminResponse.getBody().contains(inactiveDepartment.getCode()));
        assertEquals(HttpStatus.FORBIDDEN, exchangeAs("teacher1", "teacher123",
                "/api/admin/institution/departments/active", HttpMethod.GET, null).getStatusCode());

        String hireBody = "{\"username\":\"missing-department-" + suffix
                + "\",\"password\":\"TemporaryPass123\",\"fullName\":\"Missing Department Teacher\"}";
        assertEquals(HttpStatus.BAD_REQUEST, exchangeAs("admin", "admin123",
                "/api/admin/teachers/hire", HttpMethod.POST, hireBody).getStatusCode());
    }

    @Test
    void userJsonOmitsCredentialInternals() throws Exception {
        User user = new User("serialization-check", "encoded-password", Role.STUDENT);
        user.setMfaSecret("sensitive-totp-secret");
        user.setCredentialVersion(42);
        user.setPasswordChangeRequired(true);

        String serialized = objectMapper.writeValueAsString(user);

        assertFalse(serialized.contains("mfaSecret"));
        assertFalse(serialized.contains("sensitive-totp-secret"));
        assertFalse(serialized.contains("encoded-password"));
        assertFalse(serialized.contains("credentialVersion"));
        assertFalse(serialized.contains("passwordChangeRequired"));
        assertFalse(serialized.contains("usernameNormalized"));
        assertFalse(serialized.contains("emailNormalized"));

        assertFalse(objectMapper.writeValueAsString(
                new PasswordResetToken("sensitive-reset-token", user, LocalDateTime.now().plusMinutes(10)))
                .contains("sensitive-reset-token"));
        assertFalse(objectMapper.writeValueAsString(
                new EmailVerificationToken("sensitive-email-token", user, "user@example.edu",
                        LocalDateTime.now().plusMinutes(10)))
                .contains("sensitive-email-token"));
        assertFalse(objectMapper.writeValueAsString(
                new RefreshToken("sensitive-refresh-token-hash", user, LocalDateTime.now().plusMinutes(10)))
                .contains("sensitive-refresh-token-hash"));
    }

    @Test
    void mfaIsNotEnabledForUnenrolledNewAccounts() {
        User user = new User("mfa-default-check", "encoded-password", Role.STUDENT);
        assertFalse(Boolean.TRUE.equals(user.getMfaEnabled()));
    }

    @Test
    void cookiePolicyRequiresStrictSameSiteWithOriginValidation() {
        String originalPolicy = (String) ReflectionTestUtils.getField(authController, "cookieSameSite");
        try {
            ReflectionTestUtils.setField(authController, "cookieSameSite", "Lax");
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, authController::validateCookiePolicy);
            ReflectionTestUtils.setField(authController, "cookieSameSite", "strict");
            authController.validateCookiePolicy();
            assertEquals("Strict", ReflectionTestUtils.getField(authController, "cookieSameSite"));
        } finally {
            ReflectionTestUtils.setField(authController, "cookieSameSite", originalPolicy);
        }
    }

    @Test
    void generatedMfaSecretUsesValidBase32AndDecodesToTwentyBytes() {
        String secret = ReflectionTestUtils.invokeMethod(authController, "generateTotpSecret");

        assertNotNull(secret);
        assertTrue(secret.matches("[A-Z2-7]{32}"));
        byte[] decoded = ReflectionTestUtils.invokeMethod(authController, "decodeBase32", secret);
        assertEquals(20, decoded.length);
    }

    @Test
    void mfaEnrollmentAndDisableRequireRecentCredentialsAndRevokeSessions() {
        User user = userRepository.findByUsername("student1").orElseThrow();
        boolean originalMfaEnabled = Boolean.TRUE.equals(user.getMfaEnabled());
        String originalMfaSecret = user.getMfaSecret();
        int originalCredentialVersion = user.getCredentialVersion();
        boolean originalMfaSetting = (boolean) ReflectionTestUtils.getField(authController, "mfaEnabled");
        String encryptionKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        ReflectionTestUtils.setField(authController, "mfaEnabled", true);
        ReflectionTestUtils.setField(authController, "mfaSecretCipher", mfaSecretCipher);
        ReflectionTestUtils.setField(mfaSecretCipher, "encodedKey", encryptionKey);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student1", null, List.of()));

        try {
            MfaSetupRequest setup = new MfaSetupRequest();
            setup.setCurrentPassword("incorrect");
            assertEquals(HttpStatus.UNAUTHORIZED, authController.setupMfa(setup).getStatusCode());
            assertEquals(originalMfaSecret, userRepository.findByUsername("student1").orElseThrow().getMfaSecret());

            setup.setCurrentPassword("student123");
            ResponseEntity<MfaSetupResponse> setupResponse = authController.setupMfa(setup);
            assertEquals(HttpStatus.OK, setupResponse.getStatusCode());
            String secret = setupResponse.getBody().getSecret();
            assertTrue(mfaSecretCipher.isEncrypted(
                    userRepository.findByUsername("student1").orElseThrow().getMfaSecret()));

            String otp = ReflectionTestUtils.invokeMethod(authController, "generateTotp",
                    secret, java.time.Instant.now().getEpochSecond() / 30L);
            MfaVerifyRequest verify = new MfaVerifyRequest();
            verify.setCurrentPassword("incorrect");
            verify.setOtp(otp);
            assertEquals(HttpStatus.UNAUTHORIZED, authController.verifyMfa(verify).getStatusCode());

            verify.setCurrentPassword("student123");
            assertEquals(HttpStatus.OK, authController.verifyMfa(verify).getStatusCode());
            User enrolled = userRepository.findByUsername("student1").orElseThrow();
            assertTrue(Boolean.TRUE.equals(enrolled.getMfaEnabled()));
            assertEquals(originalCredentialVersion + 1, enrolled.getCredentialVersion());

            MfaDisableRequest disable = new MfaDisableRequest();
            disable.setCurrentPassword("student123");
            assertEquals(HttpStatus.UNAUTHORIZED, authController.disableMfa(disable).getStatusCode());
            disable.setOtp(ReflectionTestUtils.invokeMethod(authController, "generateTotp",
                    secret, java.time.Instant.now().getEpochSecond() / 30L));
            assertEquals(HttpStatus.OK, authController.disableMfa(disable).getStatusCode());
            User disabled = userRepository.findByUsername("student1").orElseThrow();
            assertFalse(Boolean.TRUE.equals(disabled.getMfaEnabled()));
            assertEquals(originalCredentialVersion + 2, disabled.getCredentialVersion());
        } finally {
            SecurityContextHolder.clearContext();
            ReflectionTestUtils.setField(authController, "mfaEnabled", originalMfaSetting);
            user.setMfaEnabled(originalMfaEnabled);
            user.setMfaSecret(originalMfaSecret);
            user.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(user);
        }
    }

    @Test
    void publicEndpointIsReachableWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/public/stats", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void originValidationFilterIsRegisteredForApiRequests() {
        assertTrue(securityFilterChain.getFilters("/api/auth/login").stream()
                .anyMatch(OriginValidationFilter.class::isInstance));
    }

    @Test
    void securityChainRejectsStateChangingRequestsFromUntrustedOrigins() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.addHeader("Origin", "https://attacker.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilterChain.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    @Test
    void publicPagesAreReachableAndProtectedShellsRejectAnonymousRequests() {
        ResponseEntity<String> protectedPage = restTemplate.getForEntity("/teacher-dashboard.html", String.class);
        assertEquals(HttpStatus.OK, protectedPage.getStatusCode());
        assertNotNull(protectedPage.getBody());
        assertTrue(protectedPage.getBody().contains("Access restricted"));
        ResponseEntity<String> publicHome = restTemplate.getForEntity("/", String.class);
        assertEquals(HttpStatus.OK, publicHome.getStatusCode());
        assertNotNull(publicHome.getBody());
        assertTrue(publicHome.getBody().contains("Choose how to continue"));
        ResponseEntity<String> sitemap = restTemplate.getForEntity("/sitemap.xml", String.class);
        assertEquals(HttpStatus.OK, sitemap.getStatusCode());
        assertNotNull(sitemap.getBody());
        assertTrue(sitemap.getBody().contains("<loc>http://localhost:8080/public/index.html</loc>"));
        assertTrue(sitemap.getBody().contains("<loc>http://localhost:8080/public/programmes.html</loc>"));
        assertFalse(sitemap.getBody().contains("<loc>http://localhost:8080/</loc>"));
        ResponseEntity<String> robots = restTemplate.getForEntity("/robots.txt", String.class);
        assertEquals(HttpStatus.OK, robots.getStatusCode());
        assertNotNull(robots.getBody());
        assertTrue(robots.getBody().contains("Disallow: /api/"));
        assertTrue(robots.getBody().contains("Sitemap: http://localhost:8080/sitemap.xml"));
        ResponseEntity<String> portalLogin = restTemplate.getForEntity("/index.html", String.class);
        assertEquals(HttpStatus.OK, portalLogin.getStatusCode());
        assertNotNull(portalLogin.getBody());
        assertTrue(portalLogin.getBody().contains("Student Portal"));
        for (String page : new String[]{
                "/public/index.html", "/public/about.html", "/public/programmes.html",
                "/public/admissions.html", "/public/contact.html",
                "/public/css/site.css", "/public/js/site.js"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(page, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), page);
            assertNotNull(response.getBody(), page);
            assertFalse(response.getBody().contains("Access restricted"), page);
        }
        for (String page : new String[]{
                "/terms.html", "/privacy.html", "/support.html", "/force-password-change.html"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(page, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), page);
        }
    }

    @Test
    void protectedAdminEndpointRejectsUnauthenticatedRequests() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/admin/students", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void deactivatedAccountCannotUsePreviouslyIssuedAccessToken() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        HttpHeaders accessHeaders = new HttpHeaders();
        accessHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            accessHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
        }

        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        try {
            adminService.removeUser(studentUser.getId());
            ResponseEntity<String> response = restTemplate.exchange(
                "/api/student/my-status", HttpMethod.GET, new HttpEntity<>(accessHeaders), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

            adminService.restoreUser(studentUser.getId());
            ResponseEntity<String> afterRestore = restTemplate.exchange(
                "/api/student/my-status", HttpMethod.GET, new HttpEntity<>(accessHeaders), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, afterRestore.getStatusCode(),
                "Restoring the account must not reactivate an access token issued before deactivation");
        } finally {
            studentUser.setIsActive(true);
            studentUser.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(studentUser);
        }
    }

    @Test
    void changingPasswordInvalidatesPreviouslyIssuedAccessToken() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        HttpHeaders accessHeaders = new HttpHeaders();
        accessHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            if (cookieHeader.startsWith("access_token=")) {
                accessHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
            }
        }

        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        String originalPasswordHash = studentUser.getPassword();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        try {
            ResponseEntity<String> changed = restTemplate.exchange(
                "/api/auth/change-password",
                HttpMethod.POST,
                new HttpEntity<>("{\"currentPassword\":\"student123\",\"newPassword\":\"TemporaryPass!456\"}",
                    accessHeaders),
                String.class);
            assertEquals(HttpStatus.OK, changed.getStatusCode());
            assertEquals(originalCredentialVersion + 1,
                userRepository.findByUsername("student1").orElseThrow().getCredentialVersion());

            assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.exchange("/api/student/my-status", HttpMethod.GET,
                    new HttpEntity<>(accessHeaders), String.class).getStatusCode());
        } finally {
            studentUser.setPassword(originalPasswordHash);
            studentUser.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(studentUser);
        }
    }

    @Test
    void provisionedStudentMustChangePasswordBeforePortalApiAccess() throws Exception {
        User studentUser = userRepository.findByUsername("student1").orElseThrow();
        String originalPasswordHash = studentUser.getPassword();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        boolean originalPasswordChangeRequired = studentUser.isPasswordChangeRequired();
        String temporaryPassword = "aB3_cD4-eF5_gH6-iJ7_kL8-mN9_oP0q";
        try {
            studentUser.setPassword(passwordEncoder.encode(temporaryPassword));
            studentUser.setPasswordChangeRequired(true);
            userRepository.saveAndFlush(studentUser);

            HttpHeaders loginHeaders = new HttpHeaders();
            loginHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> login = restTemplate.postForEntity(
                    "/api/auth/login",
                    new HttpEntity<>(
                            objectMapper.writeValueAsString(Map.of(
                                    "username", "student1", "password", temporaryPassword)),
                            loginHeaders),
                    String.class);
            assertEquals(HttpStatus.OK, login.getStatusCode());
            assertTrue(objectMapper.readTree(login.getBody()).get("passwordChangeRequired").asBoolean());

            HttpHeaders accessHeaders = new HttpHeaders();
            accessHeaders.setContentType(MediaType.APPLICATION_JSON);
            for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
                if (cookieHeader.startsWith("access_token=")) {
                    accessHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
                }
            }
            assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(
                    "/api/student/my-status", HttpMethod.GET, new HttpEntity<>(accessHeaders), String.class)
                    .getStatusCode());

            ResponseEntity<String> unchanged = restTemplate.exchange(
                    "/api/auth/change-password",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                            "currentPassword", temporaryPassword, "newPassword", temporaryPassword)), accessHeaders),
                    String.class);
            assertEquals(HttpStatus.BAD_REQUEST, unchanged.getStatusCode());
            assertTrue(userRepository.findByUsername("student1").orElseThrow().isPasswordChangeRequired());

            ResponseEntity<String> changed = restTemplate.exchange(
                    "/api/auth/change-password",
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                            "currentPassword", temporaryPassword, "newPassword", "TemporaryPass!456")),
                            accessHeaders),
                    String.class);
            assertEquals(HttpStatus.OK, changed.getStatusCode());
            assertFalse(userRepository.findByUsername("student1").orElseThrow().isPasswordChangeRequired());
            assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.exchange(
                    "/api/student/my-status", HttpMethod.GET, new HttpEntity<>(accessHeaders), String.class)
                    .getStatusCode());
        } finally {
            studentUser.setPassword(originalPasswordHash);
            studentUser.setCredentialVersion(originalCredentialVersion);
            studentUser.setPasswordChangeRequired(originalPasswordChangeRequired);
            userRepository.saveAndFlush(studentUser);
        }
    }

    @Test
    void changingUserRoleInvalidatesPreviouslyIssuedAccessToken() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        HttpHeaders accessHeaders = new HttpHeaders();
        accessHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            if (cookieHeader.startsWith("access_token=")) {
                accessHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
            }
        }

        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        Role originalRole = studentUser.getRole();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        try {
            UpdateUserRequest update = new UpdateUserRequest();
            update.setRole(Role.ADMIN);
            adminService.updateUser(studentUser.getId(), update);
            assertEquals(originalCredentialVersion + 1,
                userRepository.findByUsername("student1").orElseThrow().getCredentialVersion());
            assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.exchange("/api/student/my-status", HttpMethod.GET,
                    new HttpEntity<>(accessHeaders), String.class).getStatusCode());
        } finally {
            studentUser.setRole(originalRole);
            studentUser.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(studentUser);
        }
    }

    @Test
    void passwordResetTokenIsHashedAtRestAndSingleUse() {
        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        String originalPasswordHash = studentUser.getPassword();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", studentUser.getId());
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(emailService).sendPasswordReset(anyString(), anyString(), anyString());

        try {
            authenticationService.requestPasswordReset(studentUser.getEmail());
            verify(emailService).sendPasswordReset(
                org.mockito.ArgumentMatchers.eq(studentUser.getEmail()), rawTokenCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(studentUser.getUsername()));
            String rawToken = rawTokenCaptor.getValue();
            PasswordResetToken storedToken = passwordResetTokenRepository
                .findByUserAndUsedFalse(studentUser).orElseThrow();

            assertFalse(rawToken.equals(storedToken.getToken()), "The raw reset secret must not be stored");
            assertEquals(44, storedToken.getToken().length(), "Stored token should be a SHA-256 Base64 digest");
            assertTrue(authenticationService.validatePasswordResetToken(rawToken));

            authenticationService.resetPassword(rawToken, "ResetPass!123");
            assertFalse(authenticationService.validatePasswordResetToken(rawToken),
                "A consumed reset token must not be reusable");
            assertEquals(originalCredentialVersion + 1,
                userRepository.findByUsername("student1").orElseThrow().getCredentialVersion());
        } finally {
            studentUser.setPassword(originalPasswordHash);
            studentUser.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(studentUser);
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", studentUser.getId());
        }
    }

    @Test
    void passwordResetEndpointEnforcesPasswordLengthInRequestBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> tooShort = restTemplate.postForEntity(
            "/api/auth/reset-password",
            new HttpEntity<>("{\"token\":\"not-a-real-token\",\"newPassword\":\"short\"}", headers),
            String.class);
        assertEquals(HttpStatus.BAD_REQUEST, tooShort.getStatusCode());

        String tooLongPassword = "a".repeat(101);
        ResponseEntity<String> tooLong = restTemplate.postForEntity(
            "/api/auth/reset-password",
            new HttpEntity<>("{\"token\":\"not-a-real-token\",\"newPassword\":\""
                + tooLongPassword + "\"}", headers),
            String.class);
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.getStatusCode());
    }

    @Test
    void resetTokenValidationAcceptsTokenOnlyInPostBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Boolean> response = restTemplate.postForEntity(
            "/api/auth/validate-reset-token",
            new HttpEntity<>("{\"token\":\"invalid-reset-token\"}", headers),
            Boolean.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.FALSE, response.getBody());
    }

    @Test
    void passwordRecoveryEmailIsAcceptedOnlyInJsonBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> bodyRequest = restTemplate.postForEntity(
            "/api/auth/forgot-password",
            new HttpEntity<>("{\"email\":\"unknown@example.test\"}", headers),
            String.class);
        ResponseEntity<String> queryRequest = restTemplate.postForEntity(
            "/api/auth/forgot-password?email=unknown%40example.test",
            new HttpEntity<>(null, headers),
            String.class);

        assertEquals(HttpStatus.OK, bodyRequest.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, queryRequest.getStatusCode());
    }

    @Test
    void resendEmailVerificationDoesNotRevealUnknownAccounts() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/resend-email-verification",
            new HttpEntity<>("{\"email\":\"unknown@example.test\"}", headers),
            String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("If the account exists, a verification email has been sent.", response.getBody());
    }

    @Test
    void recoveryEmailProviderFailuresKeepGenericPublicResponses() {
        String recoveryEmail = "recovery-" + java.util.UUID.randomUUID() + "@example.test";
        User recoveryUser = new User("recovery-" + java.util.UUID.randomUUID(), "encoded", Role.STUDENT);
        recoveryUser.setEmail(recoveryEmail);
        recoveryUser = userRepository.saveAndFlush(recoveryUser);

        doThrow(new EmailDeliveryException("provider unavailable"))
                .when(emailService).sendPasswordReset(eq(recoveryEmail), anyString(), eq(recoveryUser.getUsername()));
        doThrow(new EmailDeliveryException("provider unavailable"))
                .when(emailService).sendEmailVerification(eq(recoveryEmail), anyString(), eq(recoveryUser.getUsername()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> body = new HttpEntity<>("{\"email\":\"" + recoveryEmail + "\"}", headers);

        try {
            ResponseEntity<String> forgot = restTemplate.postForEntity(
                    "/api/auth/forgot-password", body, String.class);
            ResponseEntity<String> resetResend = restTemplate.postForEntity(
                    "/api/auth/resend-password-reset", body, String.class);
            ResponseEntity<String> verificationResend = restTemplate.postForEntity(
                    "/api/auth/resend-email-verification", body, String.class);

            assertEquals(HttpStatus.OK, forgot.getStatusCode());
            assertEquals(HttpStatus.OK, resetResend.getStatusCode());
            assertEquals(HttpStatus.OK, verificationResend.getStatusCode());
            assertEquals("If the email is registered, password reset instructions will be sent.", forgot.getBody());
            assertEquals(forgot.getBody(), resetResend.getBody());
            assertEquals("If the account exists, a verification email has been sent.",
                    verificationResend.getBody());
            assertFalse(passwordResetTokenRepository.findByUser(recoveryUser).isPresent());
            assertFalse(emailVerificationTokenRepository.findByEmailIgnoreCase(recoveryEmail).isPresent());
            verify(emailService, org.mockito.Mockito.times(2))
                    .sendPasswordReset(eq(recoveryEmail), anyString(), eq(recoveryUser.getUsername()));
            verify(emailService)
                    .sendEmailVerification(eq(recoveryEmail), anyString(), eq(recoveryUser.getUsername()));
        } finally {
            passwordResetTokenRepository.deleteByUser(recoveryUser);
            emailVerificationTokenRepository.deleteByUser(recoveryUser);
            userRepository.delete(recoveryUser);
        }
    }

    @Test
    void emailVerificationTokenUsesPostBodiesInsteadOfUrlQueries() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Boolean> bodyRequest = restTemplate.postForEntity(
            "/api/auth/validate-email-token",
            new HttpEntity<>("{\"token\":\"invalid-verification-token\"}", headers),
            Boolean.class);
        ResponseEntity<String> queryRequest = restTemplate.postForEntity(
            "/api/auth/validate-email-token?token=invalid-verification-token",
            new HttpEntity<>(null, headers),
            String.class);

        assertEquals(HttpStatus.OK, bodyRequest.getStatusCode());
        assertEquals(Boolean.FALSE, bodyRequest.getBody());
        assertEquals(HttpStatus.BAD_REQUEST, queryRequest.getStatusCode(),
            "A verification token in the URL must not be accepted");
    }

    @Test
    void emailVerificationTokenIsHashedAtRestAndSingleUse() {
        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        String originalEmail = studentUser.getEmail();
        String verificationEmail = "verify-" + System.nanoTime() + "@example.test";
        jdbcTemplate.update("DELETE FROM email_verification_tokens WHERE user_id = ?", studentUser.getId());
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);

        try {
            authenticationService.requestEmailVerification(studentUser, verificationEmail);
            verify(emailService).sendEmailVerification(
                org.mockito.ArgumentMatchers.eq(verificationEmail), rawTokenCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(studentUser.getUsername()));
            String rawToken = rawTokenCaptor.getValue();
            EmailVerificationToken storedToken = emailVerificationTokenRepository.findByEmail(verificationEmail)
                .orElseThrow();

            assertFalse(rawToken.equals(storedToken.getToken()), "The raw verification secret must not be stored");
            assertEquals(44, storedToken.getToken().length(), "Stored token should be a SHA-256 Base64 digest");
            assertTrue(authenticationService.validateEmailVerificationToken(rawToken));

            authenticationService.confirmEmail(rawToken);
            assertEquals(verificationEmail, userRepository.findByUsername("student1").orElseThrow().getEmail());
            assertFalse(authenticationService.validateEmailVerificationToken(rawToken),
                "A consumed verification token must not be reusable");
        } finally {
            studentUser.setEmail(originalEmail);
            userRepository.saveAndFlush(studentUser);
            jdbcTemplate.update("DELETE FROM email_verification_tokens WHERE user_id = ?", studentUser.getId());
        }
    }

    @Test
    void refreshTokenIsRotatedAndCannotBeReplayed() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        String originalRefreshCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("refresh_token="))
            .findFirst().orElseThrow().split(";", 2)[0];
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, originalRefreshCookie);
        ResponseEntity<String> refreshed = restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshHeaders), String.class);
        assertEquals(HttpStatus.OK, refreshed.getStatusCode(), refreshed.getBody());

        String replacementRefreshCookie = refreshed.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("refresh_token="))
            .findFirst().orElseThrow().split(";", 2)[0];
        HttpHeaders replayHeaders = new HttpHeaders();
        replayHeaders.add(HttpHeaders.COOKIE, originalRefreshCookie);
        assertEquals(HttpStatus.UNAUTHORIZED,
            restTemplate.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(replayHeaders), String.class).getStatusCode());

        HttpHeaders replacementHeaders = new HttpHeaders();
        replacementHeaders.add(HttpHeaders.COOKIE, replacementRefreshCookie);
        ResponseEntity<String> secondRefresh = restTemplate.exchange(
            "/api/auth/refresh", HttpMethod.POST, new HttpEntity<>(replacementHeaders), String.class);
        assertEquals(HttpStatus.OK, secondRefresh.getStatusCode());
        String latestRefreshCookie = secondRefresh.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("refresh_token="))
            .findFirst().orElseThrow().split(";", 2)[0];

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, latestRefreshCookie);
        assertEquals(HttpStatus.OK,
            restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(logoutHeaders), String.class).getStatusCode());
    }

    @Test
    void revokingAllSessionsInvalidatesCurrentAccessToken() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        HttpHeaders accessHeaders = new HttpHeaders();
        accessHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            if (cookieHeader.startsWith("access_token=")) {
                accessHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
            }
        }

        var studentUser = userRepository.findByUsername("student1").orElseThrow();
        int originalCredentialVersion = studentUser.getCredentialVersion();
        try {
            assertEquals(HttpStatus.OK,
                restTemplate.exchange("/api/auth/sessions/revoke-all", HttpMethod.POST,
                    new HttpEntity<>(accessHeaders), String.class).getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED,
                restTemplate.exchange("/api/student/my-status", HttpMethod.GET,
                    new HttpEntity<>(accessHeaders), String.class).getStatusCode());
        } finally {
            studentUser.setCredentialVersion(originalCredentialVersion);
            userRepository.saveAndFlush(studentUser);
        }
    }

    @Test
    void concurrentRefreshAttemptsConsumeTokenOnlyOnce() throws Exception {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        String refreshCookie = login.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("refresh_token="))
            .findFirst().orElseThrow().split(";", 2)[0];
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpStatusCode> first = executor.submit(() -> refreshAfterStart(refreshCookie, ready, start));
            Future<HttpStatusCode> second = executor.submit(() -> refreshAfterStart(refreshCookie, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both refresh requests should be ready");
            start.countDown();

            List<HttpStatusCode> statuses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status.value() == HttpStatus.OK.value()).count());
            assertEquals(1, statuses.stream().filter(status -> status.value() == HttpStatus.UNAUTHORIZED.value()).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private HttpStatusCode refreshAfterStart(String refreshCookie, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent refresh");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, refreshCookie);
        return restTemplate.exchange("/api/auth/refresh", HttpMethod.POST,
            new HttpEntity<>(headers), String.class).getStatusCode();
    }

        @Test
        void protectedDocumentAndPortalDownloadsRejectUnauthenticatedRequests() {
        assertEquals(HttpStatus.UNAUTHORIZED,
            restTemplate.getForEntity("/api/student/documents/1/download", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
            restTemplate.getForEntity("/api/files/download?filePath=students/1/example.png", String.class).getStatusCode());
        }

        @Test
        void studentAndTeacherCannotUseAdminServiceRequestOperations() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/student/requests/admin", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/student/requests/admin/assignees", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/student/requests/admin", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/student/requests/admin/assignees", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.OK,
            exchangeAs("student1", "student123", "/api/student/requests", HttpMethod.GET, null)
                .getStatusCode());
        }

    @Test
    void serviceRequestRoleComesFromAuthenticatedUserNotRequestBody() throws Exception {
        String studentBody = "{\"type\":\"SUPPORT_TICKET\",\"subject\":\"Role integrity student test\","
            + "\"details\":\"Student request\",\"requesterRole\":\"TEACHER\"}";
        String teacherBody = "{\"type\":\"SUPPORT_TICKET\",\"subject\":\"Role integrity teacher test\","
            + "\"details\":\"Teacher request\",\"requesterRole\":\"STUDENT\"}";
        Long studentRequestId = null;
        Long teacherRequestId = null;

        try {
            ResponseEntity<String> studentResponse = exchangeAs("student1", "student123",
                "/api/student/requests", HttpMethod.POST, studentBody);
            assertEquals(HttpStatus.CREATED, studentResponse.getStatusCode());
            JsonNode studentRequest = objectMapper.readTree(studentResponse.getBody());
            assertEquals("STUDENT", studentRequest.path("requesterRole").asText());
            studentRequestId = studentRequest.path("id").asLong();

            ResponseEntity<String> teacherResponse = exchangeAs("teacher1", "teacher123",
                "/api/student/requests", HttpMethod.POST, teacherBody);
            assertEquals(HttpStatus.CREATED, teacherResponse.getStatusCode());
            JsonNode teacherRequest = objectMapper.readTree(teacherResponse.getBody());
            assertEquals("TEACHER", teacherRequest.path("requesterRole").asText());
            teacherRequestId = teacherRequest.path("id").asLong();
        } finally {
            if (studentRequestId != null) {
                serviceRequestRepository.deleteById(studentRequestId);
            }
            if (teacherRequestId != null) {
                serviceRequestRepository.deleteById(teacherRequestId);
            }
        }
    }

        @Test
        void gradeReviewRemainsAdministratorOnly() {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"teacher1\",\"password\":\"teacher123\"}", loginHeaders),
            String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());

        HttpHeaders teacherHeaders = new HttpHeaders();
        teacherHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            teacherHeaders.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
        }
        HttpEntity<String> teacherRequest = new HttpEntity<>(teacherHeaders);

        assertEquals(HttpStatus.FORBIDDEN,
            restTemplate.exchange("/api/admin/grades/pending", HttpMethod.GET, teacherRequest, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            restTemplate.exchange("/api/admin/grades/999999/approve", HttpMethod.POST, teacherRequest, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            restTemplate.exchange("/api/admin/grades/999999/review", HttpMethod.POST, teacherRequest, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            restTemplate.exchange("/api/admin/grades/999999/reject", HttpMethod.POST, teacherRequest, String.class)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/admin/grades/import",
                HttpMethod.POST, "{}").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/admin/grades/import",
                HttpMethod.POST, "{}").getStatusCode());
    }

    @Test
    void gradeApprovalPermissionCannotGrantAdministratorReviewAccess() throws Exception {
        Permission permission = permissionRepository.findByCode("GRADE_APPROVAL_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/grades/pending",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/grades/999999/approve",
                    HttpMethod.POST, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/grades/999999/review",
                    HttpMethod.POST, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/grades/999999/reject",
                    HttpMethod.POST, "{\"reason\":\"Not supported\"}").getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("student1", "student123", "/api/admin/grades/pending",
                    HttpMethod.GET, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void emailCampaignEndpointsAreAdministratorOnlyAndValidateRequests() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/admin/email/recipients", HttpMethod.GET, null)
                .getStatusCode());

        ResponseEntity<String> recipients = exchangeAs(
            "admin", "admin123", "/api/admin/email/recipients", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, recipients.getStatusCode());
        assertFalse(recipients.getBody().contains("\"password\""));

        ResponseEntity<String> emptyCampaign = exchangeAs(
            "admin", "admin123", "/api/admin/email/campaign", HttpMethod.POST,
            "{\"recipientIds\":[],\"subject\":\"Update\",\"content\":\"Hello\"}");
        assertEquals(HttpStatus.BAD_REQUEST, emptyCampaign.getStatusCode());
    }

    @Test
    void emailCampaignPermissionDelegatesAccessAndRejectsUnpermittedUsers() throws Exception {
        Permission permission = permissionRepository.findByCode("EMAIL_CAMPAIGN_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/admin/email/recipients",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                exchangeAs("teacher1", "teacher123", "/api/admin/email/campaign",
                    HttpMethod.POST,
                    "{\"recipientIds\":[],\"subject\":\"Update\",\"content\":\"Hello\"}").getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("student1", "student123", "/api/admin/email/recipients",
                    HttpMethod.GET, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void reportExportIsRestrictedToInstitutionalReportViewers() {
        String request = "{\"title\":\"Test report\",\"lines\":[\"Summary\"]}";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/reports/export", HttpMethod.POST, request)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/reports/export", HttpMethod.POST, request)
                .getStatusCode());
        assertEquals(HttpStatus.OK,
            exchangeAs("admin", "admin123", "/api/reports/export", HttpMethod.POST, request)
                .getStatusCode());
    }

    @Test
    void teacherCourseRosterEndpointsRequireTeacherRoleAndAssignedCourse() throws Exception {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/teacher/my-courses", HttpMethod.GET, null)
                .getStatusCode());

        ResponseEntity<String> coursesResponse = exchangeAs(
            "teacher1", "teacher123", "/api/teacher/my-courses", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, coursesResponse.getStatusCode());
        JsonNode courses = objectMapper.readTree(coursesResponse.getBody());
        assertTrue(courses.isArray());
        assertTrue(courses.size() > 0, "Seeded teacher should have an assigned course");

        long courseId = courses.get(0).path("id").asLong();
        assertTrue(courseId > 0);
        assertEquals(HttpStatus.OK,
            exchangeAs("teacher1", "teacher123", "/api/teacher/courses/" + courseId + "/students",
                HttpMethod.GET, null).getStatusCode());
        assertEquals(HttpStatus.OK,
            exchangeAs("teacher1", "teacher123",
                "/api/teacher/courses/" + courseId + "/assessment-components", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.OK,
            exchangeAs("admin", "admin123",
                "/api/teacher/courses/" + courseId + "/assessment-components", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123",
                "/api/teacher/courses/" + courseId + "/assessment-components", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123",
                "/api/admin/courses/" + courseId + "/assessment-components", HttpMethod.PUT, "[]")
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123",
                "/api/teacher/courses/" + courseId + "/students/1/grade", HttpMethod.POST, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123",
                "/api/teacher/grades/999999/assessment-marks", HttpMethod.GET, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123",
                "/api/admin/grades/999999/retakes", HttpMethod.POST, null)
                .getStatusCode());
    }

    @Test
    void academicViewsExcludeUnapprovedGradesAndDoNotReportAnUnverifiedCgpa() throws Exception {
        ResponseEntity<String> profileResponse = exchangeAs(
            "student1", "student123", "/api/student/profile", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
        JsonNode profile = objectMapper.readTree(profileResponse.getBody());
        long studentId = profile.path("id").asLong();
        assertTrue(studentId > 0);
        assertTrue(profile.path("cgpa").isNull());

        ResponseEntity<String> gradeStatusesResponse = exchangeAs(
            "student1", "student123", "/api/student/grade-status", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, gradeStatusesResponse.getStatusCode());
        JsonNode gradeStatuses = objectMapper.readTree(gradeStatusesResponse.getBody());
        assertTrue(gradeStatuses.isArray());
        gradeStatuses.forEach(status -> {
            assertTrue(status.has("courseCode"));
            assertTrue(status.has("courseName"));
            assertTrue(status.has("status"));
            assertTrue(!status.has("gradeId"));
            assertTrue(!status.has("gradeValue"));
            assertTrue(!status.has("gpaScore"));
            assertTrue(!status.has("rejectionReason"));
        });
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/student/grade-status", HttpMethod.GET, null)
                .getStatusCode());

        ResponseEntity<String> reportResponse = exchangeAs("student1", "student123",
            "/api/reports/academic/" + studentId, HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, reportResponse.getStatusCode());
        JsonNode report = objectMapper.readTree(reportResponse.getBody());
        assertTrue(report.path("cgpa").isNull());
        assertTrue(report.path("awardClassification").isNull());
        assertTrue(report.path("creditsEarned").isNull());
        assertTrue(report.path("latestSemester").isNull());
        assertTrue(report.path("semesterGpa").isNull());
        assertEquals(0, report.path("totalCourses").asInt());
        assertTrue(report.path("courseGrades").isEmpty());
        assertTrue(report.path("performanceSummary").asText().contains("not yet available"));

        JsonNode adminStudents = objectMapper.readTree(exchangeAs(
            "admin", "admin123", "/api/admin/students?size=20", HttpMethod.GET, null).getBody());
        assertTrue(adminStudents.path("content").isArray());
        adminStudents.path("content").forEach(student -> assertTrue(student.path("cgpa").isNull()));
    }

    @Test
    void adminStudentListingSearchesAndFiltersTheFullPagedResultSet() throws Exception {
        ResponseEntity<String> response = exchangeAs("admin", "admin123",
            "/api/admin/students?q=ALICE&active=true&size=20", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode page = objectMapper.readTree(response.getBody());
        assertEquals(1, page.path("totalElements").asInt());
        JsonNode student = page.path("content").get(0);
        assertEquals("Alice Johnson", student.path("fullName").asText());
        assertEquals("MIT/2024/00001", student.path("registrationNumber").asText());
        assertTrue(student.path("active").asBoolean());
    }

    @Test
    void adminFileListingIsRestrictedToAdministrators() throws Exception {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/files/admin/list", HttpMethod.GET, null)
                .getStatusCode());

        ResponseEntity<String> listing = exchangeAs(
            "admin", "admin123", "/api/files/admin/list", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, listing.getStatusCode());
        JsonNode response = objectMapper.readTree(listing.getBody());
        assertTrue(response.path("files").isArray());
        assertTrue(response.path("truncated").isBoolean());
    }

    @Test
    void teacherCannotUseLegacyDirectFeePaymentEndpoint() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/fees/999999/pay", HttpMethod.POST, null)
                .getStatusCode());
        ResponseEntity<String> retiredEndpoint = exchangeAs("admin", "admin123",
            "/api/fees/999999/pay", HttpMethod.POST, null);
        assertEquals(HttpStatus.GONE, retiredEndpoint.getStatusCode());
        assertTrue(retiredEndpoint.getBody().contains("payment attempt"));
    }

    @Test
    void feeManagementPermissionAllowsFeeCreationWithoutPaymentConfirmation() throws Exception {
        Permission permission = permissionRepository.findByCode("FINANCE_FEE_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        Long createdFeeId = null;
        try {
            Student student = studentRepository.findByUserUsername("student1").orElseThrow();
            ResponseEntity<String> created = exchangeAs("teacher1", "teacher123", "/api/fees",
                HttpMethod.POST, "{\"studentId\":" + student.getId()
                    + ",\"description\":\"Delegated fee test\",\"amount\":\"125.00\",\"dueDate\":\"2026-11-01\"}");
            assertEquals(HttpStatus.CREATED, created.getStatusCode());
            createdFeeId = objectMapper.readTree(created.getBody()).path("id").asLong();

            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/finance/payments/attempts/pending",
                    HttpMethod.GET, null).getStatusCode());
        } finally {
            if (createdFeeId != null) {
                feeRepository.deleteById(createdFeeId);
            }
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void feeManagementPermissionAllowsFeeListingButNotGeneralAdminAccess() {
        Permission permission = permissionRepository.findByCode("FINANCE_FEE_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/admin/fees",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/admin/students",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("student1", "student123", "/api/admin/fees",
                    HttpMethod.GET, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void financePermissionsDoNotAuthorizeUnrelatedOperations() {
        Permission permission = permissionRepository.findByCode("FINANCE_REFUND").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.BAD_REQUEST,
                exchangeAs("teacher1", "teacher123", "/api/finance/refunds", HttpMethod.POST,
                    "{\"paymentAttemptId\":999999,\"amount\":\"1.00\",\"reason\":\"test\"}")
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/finance/payments/attempts/pending",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/finance/reconciliation/entries",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/finance/holds/999999/release",
                    HttpMethod.POST, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void eachFinancePermissionAuthorizesOnlyItsOwnOperation() {
        String[][] permissionChecks = {
            {"FINANCE_PAYMENT_CONFIRM", "/api/finance/payments/attempts/pending", "GET", null},
            {"FINANCE_RECONCILIATION", "/api/finance/reconciliation/entries", "GET", null},
            {"FINANCE_HOLD_MANAGE", "/api/finance/holds/999999/release", "POST", null},
            {"FINANCE_WAIVER", "/api/finance/waivers", "POST",
                "{\"feeId\":999999,\"amount\":\"1.00\",\"reason\":\"test\"}"}
        };
        String[][] unrelatedOperations = {
            {"/api/finance/refunds", "POST",
                "{\"paymentAttemptId\":999999,\"amount\":\"1.00\",\"reason\":\"test\"}"},
            {"/api/finance/payments/attempts/pending", "GET", null},
            {"/api/finance/payments/attempts/pending", "GET", null},
            {"/api/finance/refunds", "POST",
                "{\"paymentAttemptId\":999999,\"amount\":\"1.00\",\"reason\":\"test\"}"}
        };
        HttpStatus[] expectedAllowedStatuses = {
            HttpStatus.OK, HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST
        };

        for (int index = 0; index < permissionChecks.length; index++) {
            Permission permission = permissionRepository.findByCode(permissionChecks[index][0]).orElseThrow();
            RolePermission assignment = new RolePermission();
            assignment.setRole(Role.TEACHER);
            assignment.setPermission(permission);
            rolePermissionRepository.saveAndFlush(assignment);

            try {
                ResponseEntity<String> allowed = exchangeAs("teacher1", "teacher123",
                    permissionChecks[index][1], HttpMethod.valueOf(permissionChecks[index][2]),
                    permissionChecks[index][3]);
                assertEquals(expectedAllowedStatuses[index], allowed.getStatusCode(),
                    permissionChecks[index][0] + " should authorize its own operation");

                ResponseEntity<String> unrelated = exchangeAs("teacher1", "teacher123",
                    unrelatedOperations[index][0], HttpMethod.valueOf(unrelatedOperations[index][1]),
                    unrelatedOperations[index][2]);
                assertEquals(HttpStatus.FORBIDDEN, unrelated.getStatusCode(),
                    permissionChecks[index][0] + " must not authorize unrelated operations");
            } finally {
                jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                    Role.TEACHER.name(), permission.getId());
            }
        }
    }

    @Test
    void adminPaymentAttemptConfirmationWritesAuditRecord() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Fee fee = feeRepository.saveAndFlush(new Fee(student, "Payment audit test",
                new BigDecimal("1000.00"), LocalDate.now().plusDays(30)));
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setFeeId(fee.getId());
        attempt.setStudentId(student.getId());
        attempt.setAmount(new BigDecimal("10.00"));
        attempt.setProviderReference("audit-test-" + System.nanoTime());
        PaymentAttempt saved = paymentAttemptRepository.saveAndFlush(attempt);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        ResponseEntity<String> confirmed = exchangeAs("admin", "admin123",
            "/api/finance/payments/attempts/" + saved.getId() + "/confirm", HttpMethod.POST, null);
        assertEquals(HttpStatus.OK, confirmed.getStatusCode());
        assertTrue(confirmed.getBody().contains("\"reviewedBy\":\"admin\""));
        assertTrue(confirmed.getBody().contains("Verified against provider transaction"));

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(event -> "FINANCE_PAYMENT_CONFIRMED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("Payment attempt #" + saved.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains(saved.getProviderReference()));
    }

        @Test
        void studentCanSubmitConfiguredPaymentChannelForVerification() throws Exception {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Fee fee = feeRepository.saveAndFlush(new Fee(student, "Payment channel audit test",
            new BigDecimal("75.00"), LocalDate.now().plusDays(30)));

        ResponseEntity<String> destinations = exchangeAs("student1", "student123",
            "/api/finance/payments/destinations", HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, destinations.getStatusCode());
        assertTrue(destinations.getBody().contains("MIT Test Collections"));
        assertTrue(destinations.getBody().contains("\"channel\":\"MASTERCARD\""));
        assertTrue(destinations.getBody().contains("This legacy direct-card channel is not enabled"));

        String reference = "bank-proof-" + System.nanoTime();
        String body = "{\"feeId\":" + fee.getId()
            + ",\"amount\":\"75.00\",\"paymentChannel\":\"BANK_TRANSFER\",\"providerReference\":\""
            + reference + "\"}";
        ResponseEntity<String> submitted = exchangeAs("student1", "student123",
            "/api/finance/payments/attempts", HttpMethod.POST, body);
        assertEquals(HttpStatus.CREATED, submitted.getStatusCode());
        assertTrue(submitted.getBody().contains("BANK_TRANSFER"));
        assertTrue(submitted.getBody().contains("PENDING"));

        Long studentUserId = student.getUser().getId();
        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(entry -> "FINANCE_PAYMENT_SUBMITTED".equals(entry.getAction()))
            .filter(entry -> studentUserId.equals(entry.getUserId()))
            .filter(entry -> entry.getDetails().contains(reference))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("BANK_TRANSFER"));

            assertEquals(HttpStatus.FORBIDDEN, exchangeAs("student1", "student123",
                "/api/finance/payments/attempts/pending", HttpMethod.GET, null).getStatusCode());
            ResponseEntity<String> queue = exchangeAs("admin", "admin123",
                "/api/finance/payments/attempts/pending", HttpMethod.GET, null);
            assertEquals(HttpStatus.OK, queue.getStatusCode());
            assertTrue(queue.getBody().contains(reference));

            ResponseEntity<String> rejected = exchangeAs("admin", "admin123",
                "/api/finance/payments/attempts/" + objectMapper.readTree(submitted.getBody()).get("id").asLong() + "/reject",
                HttpMethod.POST, "{\"reason\":\"Provider statement did not match\"}");
            assertEquals(HttpStatus.OK, rejected.getStatusCode());
            assertTrue(rejected.getBody().contains("FAILED"));
            assertTrue(rejected.getBody().contains("Provider statement did not match"));

            AuditLog rejection = auditLogRepository.findAll().stream()
                .filter(entry -> "FINANCE_PAYMENT_REJECTED".equals(entry.getAction()))
                .filter(entry -> userRepository.findByUsername("admin").orElseThrow().getId().equals(entry.getUserId()))
                .filter(entry -> entry.getDetails().contains(reference))
                .findFirst()
                .orElseThrow();
            assertTrue(rejection.getDetails().contains("Provider statement did not match"));
        }

    @Test
    void adminRefundWritesActorAttributedAuditRecord() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Fee fee = feeRepository.saveAndFlush(new Fee(student, "Refund audit test",
                new BigDecimal("100.00"), LocalDate.now().plusDays(30)));
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setFeeId(fee.getId());
        attempt.setStudentId(student.getId());
        attempt.setAmount(new BigDecimal("100.00"));
        attempt.setProviderReference("refund-audit-" + System.nanoTime());
        attempt.setStatus(PaymentAttemptStatus.COMPLETED);
        PaymentAttempt savedAttempt = paymentAttemptRepository.saveAndFlush(attempt);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPaymentAttemptId(savedAttempt.getId());
        allocation.setFeeId(fee.getId());
        allocation.setAmount(savedAttempt.getAmount());
        paymentAllocationRepository.saveAndFlush(allocation);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        String requestBody = "{\"paymentAttemptId\":" + savedAttempt.getId()
                + ",\"amount\":\"10.00\",\"reason\":\"Duplicate payment\"}";
        ResponseEntity<String> refund = exchangeAs("admin", "admin123",
                "/api/finance/refunds", HttpMethod.POST, requestBody);
        assertEquals(HttpStatus.CREATED, refund.getStatusCode());
        assertTrue(refund.getBody().contains("\"status\":\"PENDING\""));

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(event -> "FINANCE_REFUND_REQUESTED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("payment attempt #" + savedAttempt.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("Duplicate payment"));
    }

    @Test
    void portalSettingsPermissionCannotManageRolePermissions() {
        Permission settingsPermission = permissionRepository.findByCode("PORTAL_SETTINGS_MANAGE")
                .orElseThrow();
        boolean alreadyAssigned = rolePermissionRepository
                .existsByRoleAndPermissionId(Role.TEACHER, settingsPermission.getId());
        AssignPermissionRequest request = new AssignPermissionRequest();
        request.setRole(Role.TEACHER);
        request.setPermissionId(settingsPermission.getId());
        if (!alreadyAssigned) permissionService.assign(request, "admin");

        try {
            ResponseEntity<String> response = exchangeAs("teacher1", "teacher123",
                    "/api/permissions", HttpMethod.GET, null);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        } finally {
            if (!alreadyAssigned) permissionService.revoke(request, "admin");
        }
    }

    @Test
    void adminFeeWaiverWritesActorAttributedAuditRecord() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Fee fee = feeRepository.saveAndFlush(new Fee(student, "Waiver audit test",
                new BigDecimal("100.00"), LocalDate.now().plusDays(30)));
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        String requestBody = "{\"feeId\":" + fee.getId()
                + ",\"amount\":\"10.00\",\"reason\":\"Need-based award\"}";
        assertEquals(HttpStatus.CREATED,
            exchangeAs("admin", "admin123", "/api/finance/waivers", HttpMethod.POST, requestBody)
                .getStatusCode());

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(event -> "FINANCE_FEE_WAIVER_APPROVED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("fee #" + fee.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("Need-based award"));
    }

    @Test
    void financialHoldCreationAndReleaseAreAudited() throws Exception {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        String createBody = "{\"studentId\":" + student.getId() + ",\"reason\":\"Outstanding balance\"}";
        ResponseEntity<String> created = exchangeAs("admin", "admin123", "/api/finance/holds",
                HttpMethod.POST, createBody);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Long holdId = objectMapper.readTree(created.getBody()).get("id").asLong();

        assertEquals(HttpStatus.NO_CONTENT,
            exchangeAs("admin", "admin123", "/api/finance/holds/" + holdId + "/release",
                HttpMethod.POST, null).getStatusCode());

        List<AuditLog> events = auditLogRepository.findAll().stream()
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("Hold #" + holdId))
            .collect(java.util.stream.Collectors.toList());
        assertTrue(events.stream().anyMatch(event -> "FINANCIAL_HOLD_CREATED".equals(event.getAction())));
        assertTrue(events.stream().anyMatch(event -> "FINANCIAL_HOLD_RELEASED".equals(event.getAction())));
    }

    @Test
    void bankReconciliationImportWritesActorAttributedAuditRecord() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        String providerReference = "reconciliation-audit-" + System.nanoTime();
        String requestBody = "{\"providerReference\":\"" + providerReference
                + "\",\"statementAmount\":\"25.00\",\"statementDate\":\"2026-10-03\"}";

        assertEquals(HttpStatus.CREATED,
            exchangeAs("admin", "admin123", "/api/finance/reconciliation/entries", HttpMethod.POST, requestBody)
                .getStatusCode());

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(event -> "BANK_RECONCILIATION_IMPORTED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains(providerReference))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("UNMATCHED"));
    }

        @Test
        void exactBankReconciliationMatchConfirmsPendingTransfer() throws Exception {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Fee fee = feeRepository.saveAndFlush(new Fee(student, "Reconciled payment test",
            new BigDecimal("100.00"), LocalDate.now().plusDays(30)));
        String providerReference = "bank-match-" + System.nanoTime();
        String paymentBody = "{\"feeId\":" + fee.getId()
            + ",\"amount\":\"25.00\",\"paymentChannel\":\"BANK_TRANSFER\",\"providerReference\":\""
            + providerReference + "\"}";
        ResponseEntity<String> submitted = exchangeAs("student1", "student123",
            "/api/finance/payments/attempts", HttpMethod.POST, paymentBody);
        assertEquals(HttpStatus.CREATED, submitted.getStatusCode());
        Long attemptId = objectMapper.readTree(submitted.getBody()).get("id").asLong();

        String statementBody = "{\"providerReference\":\"" + providerReference
            + "\",\"statementAmount\":\"25.00\",\"statementDate\":\"2026-10-03\"}";
        ResponseEntity<String> reconciliation = exchangeAs("admin", "admin123",
            "/api/finance/reconciliation/entries", HttpMethod.POST, statementBody);
        assertEquals(HttpStatus.CREATED, reconciliation.getStatusCode());
        assertTrue(reconciliation.getBody().contains("MATCHED"));
        assertTrue(reconciliation.getBody().contains(String.valueOf(attemptId)));

        ResponseEntity<String> attempts = exchangeAs("student1", "student123",
            "/api/finance/payments/attempts/mine", HttpMethod.GET, null);
        assertTrue(attempts.getBody().contains("\"status\":\"COMPLETED\""));
        assertTrue(attempts.getBody().contains("\"reviewedBy\":\"admin\""));

        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        AuditLog confirmation = auditLogRepository.findAll().stream()
            .filter(event -> "FINANCE_PAYMENT_CONFIRMED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("Payment attempt #" + attemptId))
            .findFirst()
            .orElseThrow();
        assertTrue(confirmation.getDetails().contains(providerReference));
        }

    @Test
    void procurementReviewWritesActorAttributedAuditRecord() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        ProcurementRequest request = new ProcurementRequest();
        request.setRequesterId(student.getUser().getId());
        request.setDescription("Audit trail test request");
        request.setEstimatedAmount(new BigDecimal("1250.00"));
        ProcurementRequest savedRequest = procurementRequestRepository.saveAndFlush(request);
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        assertEquals(HttpStatus.OK,
            exchangeAs("admin", "admin123", "/api/procurement/requests/" + savedRequest.getId() + "/review",
                HttpMethod.POST, "{\"status\":\"APPROVED\"}").getStatusCode());

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(event -> "PROCUREMENT_REQUEST_REVIEWED".equals(event.getAction()))
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains("request #" + savedRequest.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("APPROVED"));
        assertTrue(audit.getDetails().contains("1250.00"));
    }

    @Test
    void procurementCreatesAreAuditedWithAdministratorActor() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        String suffix = String.valueOf(System.nanoTime());

        assertEquals(HttpStatus.CREATED,
            exchangeAs("admin", "admin123", "/api/procurement/suppliers", HttpMethod.POST,
                "{\"supplierCode\":\"AUDIT-" + suffix + "\",\"name\":\"Audit Supplier\"}")
                .getStatusCode());
        assertEquals(HttpStatus.CREATED,
            exchangeAs("admin", "admin123", "/api/procurement/requests", HttpMethod.POST,
                "{\"description\":\"Audit request " + suffix + "\",\"estimatedAmount\":\"125.00\"}")
                .getStatusCode());
        assertEquals(HttpStatus.CREATED,
            exchangeAs("admin", "admin123", "/api/procurement/assets", HttpMethod.POST,
                "{\"assetTag\":\"AUDIT-" + suffix + "\",\"name\":\"Audit Asset\",\"purchaseValue\":\"25.00\",\"purchaseDate\":\"2026-10-03\"}")
                .getStatusCode());

        List<AuditLog> events = auditLogRepository.findAll().stream()
            .filter(event -> adminId.equals(event.getUserId()))
            .filter(event -> event.getDetails().contains(suffix))
            .collect(java.util.stream.Collectors.toList());
        assertTrue(events.stream().anyMatch(event -> "PROCUREMENT_SUPPLIER_CREATED".equals(event.getAction())));
        assertTrue(events.stream().anyMatch(event -> "PROCUREMENT_REQUEST_CREATED".equals(event.getAction())));
        assertTrue(events.stream().anyMatch(event -> "PROCUREMENT_ASSET_CREATED".equals(event.getAction())));
    }

    @Test
    void serviceRequestStatusChangeWritesActorAndStatusTransitionToAuditLog() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        ServiceRequest request = serviceRequestRepository.saveAndFlush(
            new ServiceRequest(student, null, "SUPPORT_TICKET", "Audit test", "Audit status transition"));
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        serviceRequestService.updateStatus(request.getId(), "IN_PROGRESS", "admin").orElseThrow();

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(entry -> "SERVICE_REQUEST_STATUS_UPDATED".equals(entry.getAction()))
            .filter(entry -> adminId.equals(entry.getUserId()))
            .filter(entry -> entry.getDetails().contains("Request #" + request.getId()))
            .findFirst()
            .orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(audit.getDetails().contains("OPEN to IN_PROGRESS"));
        assertTrue(request.getUpdatedAt() != null);
        assertTrue(serviceRequestEventRepository.findForRequestInOrder(request.getId()).stream()
                .anyMatch(event -> "STATUS_CHANGED".equals(event.getEventType())
                        && "OPEN".equals(event.getFromStatus())
                        && "IN_PROGRESS".equals(event.getToStatus())
                        && "admin".equals(event.getActorUsername())));
    }

    @Test
    void adminCanAssignServiceRequestAndReadItsTimestampedHistory() throws Exception {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        User requester = userRepository.findByUsername("student1").orElseThrow();
        Long requestId = serviceRequestService.create(
                student, requester, "SUPPORT_TICKET", "Assignment test", "Assign to admin").getId();
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        try {
            ResponseEntity<String> assignmentResponse = exchangeAs(
                    "admin", "admin123", "/api/student/requests/admin/" + requestId + "/assignee",
                    HttpMethod.PATCH, "{\"assigneeId\":" + adminId + "}");
            assertEquals(HttpStatus.OK, assignmentResponse.getStatusCode());
            assertTrue(objectMapper.readTree(assignmentResponse.getBody()).path("assigneeId").asLong() == adminId);

            ResponseEntity<String> historyResponse = exchangeAs(
                    "admin", "admin123", "/api/student/requests/admin/" + requestId + "/history",
                    HttpMethod.GET, null);
            assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
            JsonNode history = objectMapper.readTree(historyResponse.getBody());
            assertTrue(history.size() >= 2);
            assertTrue(java.util.stream.StreamSupport.stream(history.spliterator(), false)
                    .anyMatch(event -> "CREATED".equals(event.path("eventType").asText())
                            && event.path("createdAt").isTextual()));
            assertTrue(java.util.stream.StreamSupport.stream(history.spliterator(), false)
                    .anyMatch(event -> "ASSIGNED".equals(event.path("eventType").asText())
                            && "admin".equals(event.path("toAssigneeUsername").asText())
                            && "admin".equals(event.path("actorUsername").asText())));
        } finally {
            serviceRequestRepository.deleteById(requestId);
        }
    }

    @Test
    void examPermitApprovalAndRejectionAreAudited() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();
        Course course = courseRepository.findAll().stream().findFirst().orElseThrow();
        Exam exam = new Exam();
        exam.setCourseId(course.getId());
        exam.setTitle("Audit exam " + System.nanoTime());
        exam.setStartsAt(java.time.LocalDateTime.now().plusDays(10));
        exam.setVenue("Audit Hall");
        Exam savedExam = examRepository.saveAndFlush(exam);

        ExamPermit permit = new ExamPermit();
        permit.setStudentId(student.getId());
        permit.setExamId(savedExam.getId());
        permit.setStatus(ExamPermitStatus.PENDING);
        permit.setRequestedAt(java.time.LocalDateTime.now());
        ExamPermit savedPermit = examPermitRepository.saveAndFlush(permit);

        assertEquals(HttpStatus.OK,
            exchangeAs("admin", "admin123", "/api/admin/exam-permits/" + savedPermit.getId() + "/approve", HttpMethod.POST, null).getStatusCode());

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(entry -> adminId.equals(entry.getUserId()))
            .filter(entry -> "EXAM_PERMIT_APPROVED".equals(entry.getAction()))
            .filter(entry -> entry.getDetails().contains("Permit #" + savedPermit.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("student #" + student.getId()));
    }

    @Test
    void hrLeaveReviewWritesActorAndDecisionToAuditLog() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        com.sis.entity.User teacherUser = userRepository.findByUsername("teacher1").orElseThrow();

        Staff staff = new Staff();
        staff.setUser(teacherUser);
        staff.setEmployeeNumber("HR-AUDIT-" + System.nanoTime());
        staff.setFullName("Audit Teacher");
        staff.setTitle("Lecturer");
        staff.setDepartmentId(1L);
        staff.setHireDate(LocalDate.now().minusYears(1));
        staff.setActive(true);
        Staff savedStaff = staffRepository.saveAndFlush(staff);

        LeaveRequest leave = new LeaveRequest();
        leave.setStaff(savedStaff);
        leave.setStartDate(LocalDate.now().plusDays(5));
        leave.setEndDate(LocalDate.now().plusDays(7));
        leave.setReason("Audit review test");
        LeaveRequest savedLeave = leaveRequestRepository.saveAndFlush(leave);

        assertEquals(HttpStatus.OK,
            exchangeAs("admin", "admin123", "/api/hr/leave/" + savedLeave.getId() + "/review",
                HttpMethod.POST, "{\"status\":\"APPROVED\"}").getStatusCode());

        AuditLog audit = auditLogRepository.findAll().stream()
            .filter(entry -> "HR_LEAVE_REVIEWED".equals(entry.getAction()))
            .filter(entry -> adminId.equals(entry.getUserId()))
            .filter(entry -> entry.getDetails().contains("Leave request #" + savedLeave.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(audit.getDetails().contains("APPROVED"));
    }

    @Test
    void qualityAccreditationReviewIsAudited() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        Campus campus = campusRepository.findAll().stream().findFirst().orElseGet(() -> {
            Campus newCampus = new Campus();
            newCampus.setCode("AUDIT-CAMPUS-" + System.nanoTime());
            newCampus.setName("Audit Campus");
            newCampus.setAddress("Audit Address");
            newCampus.setActive(true);
            return campusRepository.saveAndFlush(newCampus);
        });
        com.sis.entity.Faculty faculty = facultyRepository.findAll().stream().findFirst().orElseGet(() -> {
            com.sis.entity.Faculty newFaculty = new com.sis.entity.Faculty();
            newFaculty.setCode("AUDIT-FACULTY-" + System.nanoTime());
            newFaculty.setName("Audit Faculty");
            newFaculty.setCampus(campus);
            newFaculty.setActive(true);
            return facultyRepository.saveAndFlush(newFaculty);
        });
        Department department = departmentRepository.findAll().stream().findFirst().orElseGet(() -> {
            Department newDepartment = new Department();
            newDepartment.setCode("AUDIT-DEPT-" + System.nanoTime());
            newDepartment.setName("Audit Department");
            newDepartment.setFaculty(faculty);
            newDepartment.setActive(true);
            return departmentRepository.saveAndFlush(newDepartment);
        });
        Program program = new Program();
        program.setCode("AUDIT-QUALITY-" + System.nanoTime());
        program.setName("Audit Program");
        program.setAwardTitle("Audit Award");
        program.setDepartment(department);
        program.setDurationYears(3);
        program.setActive(true);
        Program savedProgram = programRepository.saveAndFlush(program);

        AccreditationRecord record = new AccreditationRecord();
        record.setProgram(savedProgram);
        record.setAccreditingBody("Audit Body");
        record.setCertificateReference("CERT-AUDIT-" + System.nanoTime());
        record.setStatus(AccreditationStatus.SUBMITTED);
        record.setValidFrom(LocalDate.now());
        record.setValidUntil(LocalDate.now().plusYears(1));
        record.setEvidencePath("/tmp/audit.pdf");
        AccreditationRecord savedRecord = accreditationRecordRepository.saveAndFlush(record);

        com.sis.dto.ReviewAccreditationRequest reviewRequest = new com.sis.dto.ReviewAccreditationRequest();
        reviewRequest.setStatus(AccreditationStatus.UNDER_REVIEW);
        qualityService.review(savedRecord.getId(), reviewRequest, "admin");

        List<AuditLog> audits = auditLogRepository.findAll().stream()
            .filter(entry -> "QUALITY_ACREDITATION_REVIEWED".equals(entry.getAction()))
            .filter(entry -> adminId.equals(entry.getUserId()))
            .collect(java.util.stream.Collectors.toList());
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().anyMatch(entry -> entry.getDetails().contains("UNDER_REVIEW")
            || entry.getDetails().contains("Accreditation record #" + savedRecord.getId())));
    }

    @Test
    void permissionCreationAssignmentAndRevocationAreAudited() {
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        String code = "AUDIT_TEST_" + System.nanoTime();
        CreatePermissionRequest createRequest = new CreatePermissionRequest();
        createRequest.setCode(code);
        createRequest.setDescription("Temporary permission audit test");
        Permission created = permissionService.create(createRequest, "admin");

        AssignPermissionRequest assignmentRequest = new AssignPermissionRequest();
        assignmentRequest.setRole(Role.TEACHER);
        assignmentRequest.setPermissionId(created.getId());

        try {
            permissionService.assign(assignmentRequest, "admin");
            permissionService.revoke(assignmentRequest, "admin");

            List<AuditLog> events = auditLogRepository.findAll().stream()
                .filter(event -> adminId.equals(event.getUserId()))
                .filter(event -> event.getDetails().contains(created.getCode()))
                .collect(java.util.stream.Collectors.toList());
            assertTrue(events.stream().anyMatch(event -> "PERMISSION_CREATED".equals(event.getAction())));
            assertTrue(events.stream().anyMatch(event -> "ROLE_PERMISSION_ASSIGNED".equals(event.getAction())));
            assertTrue(events.stream().anyMatch(event -> "ROLE_PERMISSION_REVOKED".equals(event.getAction())));
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), created.getId());
            permissionRepository.deleteById(created.getId());
        }
    }

    @Test
    void serviceRequestPermissionCanBeDelegatedWithoutGrantingAdminRole() {
        Permission permission = permissionRepository.findByCode("SERVICE_REQUEST_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/student/requests/admin", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.NOT_FOUND,
                exchangeAs("teacher1", "teacher123", "/api/student/requests/admin/999999", HttpMethod.GET, null)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void registrationPermissionCanManageCurriculumWithoutAdminRole() {
        String requestBody = "{\"programId\":999999,\"courseId\":999999,\"semesterNumber\":1,\"required\":true}";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/curriculum/courses", HttpMethod.POST, requestBody)
                .getStatusCode());

        Permission permission = permissionRepository.findByCode("REGISTRATION_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.BAD_REQUEST,
                exchangeAs("teacher1", "teacher123", "/api/curriculum/courses", HttpMethod.POST, requestBody)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void examPermitPermissionCanReviewPermitQueueWithoutAdminRole() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/admin/exam-permits/pending", HttpMethod.GET, null)
                .getStatusCode());

        Permission permission = permissionRepository.findByCode("EXAM_PERMIT_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/admin/exam-permits/pending", HttpMethod.GET, null)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void examResultPublicationRequiresAdministratorAccess() {
        String endpoint = "/api/academic/exam-results/999999/publish";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, null).getStatusCode());

        Permission permission = permissionRepository.findByCode("EXAM_RESULT_PUBLISH").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void qualityReviewPermissionCanReviewAccreditationWithoutAdminRole() {
        String endpoint = "/api/quality/accreditations/999999/review";
        String requestBody = "{\"status\":\"ACCREDITED\"}";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, requestBody).getStatusCode());

        Permission permission = permissionRepository.findByCode("QUALITY_REVIEW").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.BAD_REQUEST,
                exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, requestBody).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void researchReviewPermissionCanReviewThesisWithoutAdminRole() {
        String endpoint = "/api/research/theses/999999/review";
        String requestBody = "{\"status\":\"APPROVED\"}";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, requestBody).getStatusCode());

        Permission permission = permissionRepository.findByCode("RESEARCH_REVIEW").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.BAD_REQUEST,
                exchangeAs("teacher1", "teacher123", endpoint, HttpMethod.POST, requestBody).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void admissionsReviewPermissionCanReviewApplicationsWithoutGrantingProcurement() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/admissions/applications", HttpMethod.GET, null)
                .getStatusCode());

        Permission permission = permissionRepository.findByCode("ADMISSIONS_REVIEW").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/admissions/applications", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/procurement/suppliers", HttpMethod.GET, null)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void academicOperationsPermissionPassesTeacherPathGateWithoutGrantingProcurement() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/teacher/me", HttpMethod.GET, null)
                .getStatusCode());

        Permission permission = permissionRepository.findByCode("ACADEMIC_OPS_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.STUDENT);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            ResponseEntity<String> teacherEndpoint = exchangeAs(
                "student1", "student123", "/api/teacher/me", HttpMethod.GET, null);
            assertEquals(HttpStatus.NOT_FOUND, teacherEndpoint.getStatusCode(),
                "Delegated access should pass path and method security, then report no teacher profile");
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("student1", "student123", "/api/procurement/suppliers", HttpMethod.GET, null)
                    .getStatusCode(),
                "Academic operations permission must not grant procurement access");
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.STUDENT.name(), permission.getId());
        }
    }

    @Test
    void procurementPermissionCanManageProcurementWithoutGrantingHrOrLibrary() {
        String suppliersEndpoint = "/api/procurement/suppliers";
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", suppliersEndpoint, HttpMethod.GET, null).getStatusCode());

        Permission permission = permissionRepository.findByCode("PROCUREMENT_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", suppliersEndpoint, HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/hr/staff", HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/library/items", HttpMethod.POST,
                    "{\"title\":\"Delegation test\",\"author\":\"Test\",\"isbn\":\"TEST-123\",\"totalCopies\":1}")
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/finance/holds/999999/release",
                    HttpMethod.POST, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void hrPermissionCanManageStaffWithoutGrantingLibraryOrAccommodation() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/hr/staff", HttpMethod.GET, null).getStatusCode());

        Permission permission = permissionRepository.findByCode("HR_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/hr/staff", HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/hr/leave/pending", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/library/items", HttpMethod.POST,
                    "{\"title\":\"HR boundary test\",\"author\":\"Test\",\"isbn\":\"HR-BOUNDARY-1\",\"totalCopies\":1}")
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/accommodation/rooms", HttpMethod.POST,
                    "{\"roomCode\":\"HR-BOUNDARY-1\",\"building\":\"Test Building\",\"capacity\":1}")
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void libraryPermissionCanManageCatalogWithoutGrantingHrOrAccommodation() {
        String itemEndpoint = "/api/library/items";
        String isbn = "LIB-" + System.nanoTime();
        String createItemBody = "{\"title\":\"Delegated library item\",\"author\":\"Test\",\"isbn\":\""
            + isbn + "\",\"totalCopies\":1}";

        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", itemEndpoint, HttpMethod.POST, createItemBody).getStatusCode());

        Permission permission = permissionRepository.findByCode("LIBRARY_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.CREATED,
                exchangeAs("teacher1", "teacher123", itemEndpoint, HttpMethod.POST, createItemBody).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/hr/staff", HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/accommodation/rooms", HttpMethod.POST,
                    "{\"roomCode\":\"LIB-BOUNDARY-" + System.nanoTime()
                        + "\",\"building\":\"Test Building\",\"capacity\":1}")
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void accommodationPermissionCanViewRoomsWithoutGrantingOtherOperations() {
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/accommodation/rooms",
                HttpMethod.GET, null).getStatusCode());

        Permission permission = permissionRepository.findByCode("ACCOMMODATION_MANAGE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/accommodation/rooms",
                    HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/hr/staff",
                    HttpMethod.GET, null).getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

            @Test
            void academicReportsAreLimitedToStudentSelfTeacherAssignmentsOrAdmin() {
            Student assignedStudent = studentRepository.findByUserUsername("student1").orElseThrow();
            Student unassignedStudent = studentRepository.findByUserUsername("student2").orElseThrow();
            Teacher teacher = teacherRepository.findByUserUsername("teacher1").orElseThrow();
            Course course = courseRepository.findByTeacherId(teacher.getId()).stream().findFirst().orElseThrow();
            jdbcTemplate.update("DELETE FROM course_students WHERE course_id = ? AND student_id = ?",
                course.getId(), unassignedStudent.getId());
            if (!courseRepository.existsAssignedStudent(teacher.getId(), assignedStudent.getId())) {
                jdbcTemplate.update("INSERT INTO course_students (course_id, student_id) VALUES (?, ?)",
                    course.getId(), assignedStudent.getId());
            }

            assertEquals(HttpStatus.OK,
                exchangeAs("student1", "student123", "/api/reports/academic/" + assignedStudent.getId(), HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", "/api/reports/academic/" + assignedStudent.getId(), HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/reports/academic/" + unassignedStudent.getId(), HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/reports/class/Grade10", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/reports/top-students?className=Grade10", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("admin", "admin123", "/api/reports/academic/" + unassignedStudent.getId(), HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("admin", "admin123", "/api/reports/class/Grade10", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("admin", "admin123", "/api/reports/top-students?className=Grade10", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.NOT_FOUND,
                exchangeAs("admin", "admin123", "/api/teacher/my-courses", HttpMethod.GET, null)
                    .getStatusCode());
            }

    @Test
    void studentReportApprovalRequiresAdminAndSubmittedGrades() {
        Student student = studentRepository.findByUserUsername("student1").orElseThrow();

        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("student1", "student123", "/api/reports/academic/" + student.getId() + "/approve", HttpMethod.POST, null)
                .getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", "/api/reports/academic/" + student.getId() + "/approve", HttpMethod.POST, null)
                .getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
            exchangeAs("admin", "admin123", "/api/reports/academic/" + student.getId() + "/approve", HttpMethod.POST, null)
                .getStatusCode());

        Permission approvalPermission = permissionRepository.findByCode("REPORTS_APPROVE").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(approvalPermission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/reports/academic/" + student.getId() + "/approve", HttpMethod.POST, null)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), approvalPermission.getId());
        }
    }

    @Test
    void reportsPermissionDelegatesBroadReportingWithoutGrantingAdminAccess() {
        Student firstStudent = studentRepository.findByUserUsername("student1").orElseThrow();
        Student secondStudent = studentRepository.findByUserUsername("student2").orElseThrow();
        Teacher teacher = teacherRepository.findByUserUsername("teacher1").orElseThrow();
        String classReport = "/api/reports/class/Grade10";

        assertEquals(HttpStatus.FORBIDDEN,
            exchangeAs("teacher1", "teacher123", classReport, HttpMethod.GET, null).getStatusCode());

        Permission permission = permissionRepository.findByCode("REPORTS_VIEW").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123", classReport, HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123",
                    "/api/reports/academic/" + secondStudent.getId(), HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123",
                    "/api/reports/compare?studentId1=" + firstStudent.getId() + "&studentId2="
                        + secondStudent.getId(), HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123",
                    "/api/reports/teacher/" + teacher.getId(), HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.OK,
                exchangeAs("teacher1", "teacher123",
                    "/api/reports/top-students?className=Grade10", HttpMethod.GET, null).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/portal/admin/audit", HttpMethod.GET, null)
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), permission.getId());
        }
    }

        @Test
        void onlyAdministratorsCanDeleteStoredFiles() {
        ResponseEntity<String> response = exchangeAs(
            "student1", "student123", "/api/files/delete?filePath=students/1/example.png", HttpMethod.DELETE, null);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

    @Test
    void loginWithValidCredentialsSetsHttpOnlyCookiesAndReturnsNoRawToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"student1\",\"password\":\"student123\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Response body must never contain the raw token (see AuthResponse's Javadoc) -
        // only role/username.
        assertNotNull(response.getBody());
        assertFalse(response.getBody().contains("\"token\""), "Response body leaked a raw token field");
        assertTrue(response.getBody().contains("\"role\":\"STUDENT\""));

        // The access token must be set as an HttpOnly cookie, not merely a cookie.
        var setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeaders);
        boolean hasHttpOnlyAccessToken = setCookieHeaders.stream()
                .anyMatch(c -> c.contains("access_token=") && c.toLowerCase().contains("httponly"));
        assertTrue(hasHttpOnlyAccessToken, "access_token cookie missing or not HttpOnly");
    }

    @Test
    void authenticatedAuthorityEndpointIncludesRoleAndDelegatedPermissions() {
        Permission permission = permissionRepository.findByCode("FINANCE_PAYMENT_CONFIRM").orElseThrow();
        RolePermission assignment = new RolePermission();
        assignment.setRole(Role.TEACHER);
        assignment.setPermission(permission);
        rolePermissionRepository.saveAndFlush(assignment);

        try {
            ResponseEntity<String> response = exchangeAs("teacher1", "teacher123",
                    "/api/auth/authorities", HttpMethod.GET, null);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("\"TEACHER\""));
            assertTrue(response.getBody().contains("\"PERM_FINANCE_PAYMENT_CONFIRM\""));
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                    Role.TEACHER.name(), permission.getId());
        }
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"student1\",\"password\":\"wrong-password\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

        @Test
        void authenticatedUsersCanLoadAndSaveSharedPreferences() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
            "/api/auth/login",
            new HttpEntity<>("{\"username\":\"student1\",\"password\":\"student123\"}", headers),
            String.class);

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : loginResponse.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            String cookie = cookieHeader.split(";", 2)[0];
            authHeaders.add(HttpHeaders.COOKIE, cookie);
        }

            ResponseEntity<String> getResponse = restTemplate.exchange(
                "/api/portal/preferences",
                HttpMethod.GET,
            new HttpEntity<>(authHeaders),
                String.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertNotNull(getResponse.getBody());

        HttpEntity<String> updateRequest = new HttpEntity<>(
            "{\"language\":\"Luganda\",\"theme\":\"dark\",\"settingsJson\":\"{\\\"privacy\\\":[true,false,true,true],\\\"notifications\\\":[true,false,true,true]}\"}",
                authHeaders);

        ResponseEntity<String> putResponse = restTemplate.exchange(
            "/api/portal/preferences",
            HttpMethod.PUT,
            updateRequest,
            String.class);

        assertEquals(HttpStatus.OK, putResponse.getStatusCode());
        assertTrue(putResponse.getBody().contains("\"language\":\"Luganda\""));
        assertTrue(putResponse.getBody().contains("\"theme\":\"dark\""));
        }

    @Test
    void delegatedPortalPermissionsStayScopedToTheirOwnOperations() {
        Permission assignmentPermission = permissionRepository.findByCode("ASSIGNMENT_MANAGE").orElseThrow();
        RolePermission assignmentGrant = new RolePermission();
        assignmentGrant.setRole(Role.TEACHER);
        assignmentGrant.setPermission(assignmentPermission);
        rolePermissionRepository.saveAndFlush(assignmentGrant);

        try {
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/portal/admin/settings", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/portal/admin/audit", HttpMethod.GET, null)
                    .getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                exchangeAs("teacher1", "teacher123", "/api/portal/admin/exams", HttpMethod.POST,
                    "{\"courseId\":1,\"title\":\"Scoped exam\",\"startsAt\":\"2026-12-01T09:00:00\",\"venue\":\"A1\"}")
                    .getStatusCode());
        } finally {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_name = ? AND permission_id = ?",
                Role.TEACHER.name(), assignmentPermission.getId());
        }
    }

    @Test
    void publicRegistrationCannotEscalateToAdminRole() {
        // This is the exact vulnerability fixed earlier in this project's history:
        // register used to accept a role chosen by the caller. Confirms the fix holds -
        // whatever the caller sends, the account that results can only ever be STUDENT.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"selfRegisteredTestUser\",\"password\":\"testPass123\"}";

        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertTrue(loginResponse.getBody().contains("\"role\":\"STUDENT\""));
        assertFalse(loginResponse.getBody().contains("\"role\":\"ADMIN\""));
    }

    private ResponseEntity<String> exchangeAs(String username, String password, String path,
                                               HttpMethod method, String body) {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = restTemplate.postForEntity(
                "/api/auth/login",
                new HttpEntity<>(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password), loginHeaders),
                String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode(), "login failed for " + username);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String cookieHeader : login.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of())) {
            headers.add(HttpHeaders.COOKIE, cookieHeader.split(";", 2)[0]);
        }
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, method, request, String.class);
    }

    private void assertPageDeniedFor(String username, String password, String path) {
        ResponseEntity<String> response = exchangeAs(username, password, path, HttpMethod.GET, null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), path);
        assertNotNull(response.getBody(), path);
        assertTrue(response.getBody().contains("Access restricted"), path);
    }
}
