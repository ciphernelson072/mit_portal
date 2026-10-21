package com.sis.service;

import com.sis.entity.User;
import com.sis.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User save(User user) {
        if (user == null) {
            return null;
        }

        String password = user.getPassword();
        if (password != null && isAlreadyHashed(password)) {
            return userRepository.save(user);
        }

        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    private boolean isAlreadyHashed(String password) {
        return password != null && (
                password.startsWith("$2a$") ||
                password.startsWith("$2b$") ||
                password.startsWith("$2y$")
        );
    }

    public Optional<User> findByUsername(String username) {
        return username == null ? Optional.empty() : userRepository.findByUsernameIgnoreCase(username.trim());
    }
}
