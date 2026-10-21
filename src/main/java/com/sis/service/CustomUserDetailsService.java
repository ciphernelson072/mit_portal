package com.sis.service;

import com.sis.entity.User;
import com.sis.repository.UserRepository;
import com.sis.repository.RolePermissionRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public CustomUserDetailsService(UserRepository userRepository, RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? null : username.trim();
        User user = userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        java.util.List<String> authorities = new java.util.ArrayList<>();
        if (user.isPasswordChangeRequired()) {
            authorities.add("PASSWORD_CHANGE_REQUIRED");
        } else {
            authorities.add(user.getRole().name());
            rolePermissionRepository.findByRole(user.getRole()).stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getPermission().getActive()))
                .map(assignment -> "PERM_" + assignment.getPermission().getCode())
                .forEach(authorities::add);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities.toArray(new String[0]))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                // This used to be hardcoded to false, meaning a deactivated account
                // (AdminService.removeUser/removeStudent/removeTeacher - is_active=0)
                // could still authenticate successfully. Spring Security's
                // DaoAuthenticationProvider checks this flag and rejects login with
                // a DisabledException when true - now it actually reflects the
                // account's real state.
                .disabled(!Boolean.TRUE.equals(user.getIsActive()))
                .build();
    }

    public Optional<Integer> getCredentialVersion(String username) {
        String normalizedUsername = username == null ? null : username.trim();
        return userRepository.findByUsernameIgnoreCase(normalizedUsername).map(User::getCredentialVersion);
    }
}
