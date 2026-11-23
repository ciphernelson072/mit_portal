package com.sis.service;

import com.sis.entity.User;
import com.sis.exception.EmailDeliveryException;
import com.sis.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UserEmailVerificationEventListener {
    private static final Logger logger = LoggerFactory.getLogger(UserEmailVerificationEventListener.class);

    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    public UserEmailVerificationEventListener(UserRepository userRepository,
                                              AuthenticationService authenticationService) {
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(UserEmailVerificationRequestedEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("Email verification account no longer exists"));
        try {
            authenticationService.requestEmailVerification(user, event.email());
        } catch (EmailDeliveryException failure) {
            logger.warn("Email verification delivery failed for user id {}; the account remains unverified and can request another link.",
                    event.userId(), failure);
        }
    }
}
