package com.sis.service;

import com.sis.entity.MfaRecoveryCode;
import com.sis.entity.User;
import com.sis.repository.MfaRecoveryCodeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MfaRecoveryCodeServiceTest {
    @Test
    void replacesCodesAndConsumesACodeOnlyOnce() {
        MfaRecoveryCodeRepository repository = mock(MfaRecoveryCodeRepository.class);
        MfaRecoveryCodeService service = new MfaRecoveryCodeService(repository);
        User user = new User("student", "encoded", com.sis.entity.Role.STUDENT);
        user.setId(42L);
        when(repository.consume(eq(42L), anyString(), any())).thenReturn(1);

        List<String> codes = service.replaceCodes(user);

        assertThat(codes).hasSize(10);
        assertThat(codes).allSatisfy(code ->
                assertThat(code).matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        verify(repository).deleteByUserId(42L);
        verify(repository, times(10)).save(any(MfaRecoveryCode.class));
        assertThat(service.consume(user, codes.get(0))).isTrue();
        verify(repository).consume(eq(42L), eq(service.hash(codes.get(0))), any());
    }

    @Test
    void rejectsMalformedAndWrongLengthRecoveryCodes() {
        MfaRecoveryCodeRepository repository = mock(MfaRecoveryCodeRepository.class);
        MfaRecoveryCodeService service = new MfaRecoveryCodeService(repository);
        User user = new User("student", "encoded", com.sis.entity.Role.STUDENT);
        user.setId(42L);

        assertThat(service.consume(user, "123456")).isFalse();
        assertThat(service.consume(user, "short")).isFalse();
        assertThat(service.consume(null, "ABCDE-12345")).isFalse();
        verify(repository, never()).consume(any(), anyString(), any());
    }
}
