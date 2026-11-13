package com.sis.service;

import com.sis.entity.EmailVerificationToken;
import com.sis.entity.User;
import com.sis.repository.EmailVerificationTokenRepository;
import com.sis.repository.PasswordResetTokenRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationEmailFlowTest {
    @Test
    void passwordResetSendsToStoredAccountAddressAfterNormalizingSubmittedEmail() {
        UserRepository users = mock(UserRepository.class);
        PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        EmailService emailService = mock(EmailService.class);
        AuthenticationService service = new AuthenticationService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordResetTokenRepository", resetTokens);
        ReflectionTestUtils.setField(service, "emailVerificationTokenRepository", verificationTokens);
        ReflectionTestUtils.setField(service, "emailService", emailService);

        User user = new User("student", "encoded", com.sis.entity.Role.STUDENT);
        user.setEmail("Student@Example.test");
        when(users.findByEmailIgnoreCase("student@example.test")).thenReturn(Optional.of(user));
        doNothing().when(resetTokens).deleteByUser(user);
        when(resetTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailService).sendPasswordReset(anyString(), anyString(), anyString());

        service.requestPasswordReset("  STUDENT@example.test  ");

        verify(users).findByEmailIgnoreCase("student@example.test");
        verify(emailService).sendPasswordReset(eq("Student@Example.test"), anyString(), eq("student"));
    }

    @Test
    void emailVerificationResendUsesStoredAccountAddressAfterTrimmedCaseInsensitiveLookup() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        EmailService emailService = mock(EmailService.class);
        AuthenticationService service = new AuthenticationService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "emailVerificationTokenRepository", verificationTokens);
        ReflectionTestUtils.setField(service, "passwordResetTokenRepository",
                mock(PasswordResetTokenRepository.class));
        ReflectionTestUtils.setField(service, "emailService", emailService);
        User user = new User("student", "encoded", com.sis.entity.Role.STUDENT);
        user.setEmail("Student@Example.test");
        when(users.findByEmailNormalized("Student@Example.test")).thenReturn(Optional.of(user));
        when(verificationTokens.findByEmailIgnoreCase("Student@Example.test")).thenReturn(Optional.empty());
        when(verificationTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailService).sendEmailVerification(anyString(), anyString(), anyString());

        service.resendEmailVerification("  Student@Example.test  ");

        verify(users).findByEmailNormalized("Student@Example.test");
        verify(emailService).sendEmailVerification(
                eq("Student@Example.test"), anyString(), eq("student"));
        org.mockito.ArgumentCaptor<EmailVerificationToken> token =
                org.mockito.ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(verificationTokens).save(token.capture());
        assertThat(token.getValue().getEmail()).isEqualTo("Student@Example.test");
    }
}
