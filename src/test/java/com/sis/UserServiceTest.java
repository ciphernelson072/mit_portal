package com.sis;

import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.UserRepository;
import com.sis.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void saveDoesNotDoubleEncodeExistingBcryptHash() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode("rawPassword");

        User user = new User("alice", hashedPassword, Role.STUDENT);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserService service = new UserService(userRepository, passwordEncoder);
        User saved = service.save(user);

        assertEquals(hashedPassword, saved.getPassword());
    }
}
