package com.sis.service;

import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceCaseInsensitiveTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(repository, encoder);

    @Test
    void usernameLookupMatchesCaseInsensitively() {
        User user = new User();
        user.setUsername("Alice");

        when(repository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

        assertThat(service.findByUsername("alice")).contains(user);
    }

    @Test
    void entityNormalizesIdentityFieldsForCaseInsensitiveUniqueness() {
        User user = new User("  Alice  ", "pw", Role.STUDENT);
        user.setEmail("  Alice@Example.COM  ");

        assertThat(user.getUsername()).isEqualTo("Alice");
        assertThat(user.getUsernameNormalized()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("Alice@Example.COM");
        assertThat(user.getEmailNormalized()).isEqualTo("alice@example.com");
    }
}
