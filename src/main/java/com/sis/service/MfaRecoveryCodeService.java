package com.sis.service;

import com.sis.entity.MfaRecoveryCode;
import com.sis.entity.User;
import com.sis.repository.MfaRecoveryCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class MfaRecoveryCodeService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MfaRecoveryCodeRepository codes;
    private OperationalMetricsService metrics;

    public MfaRecoveryCodeService(MfaRecoveryCodeRepository codes) {
        this.codes = codes;
    }

    @Autowired
    public MfaRecoveryCodeService(MfaRecoveryCodeRepository codes, OperationalMetricsService metrics) {
        this.codes = codes;
        this.metrics = metrics;
    }

    public List<String> replaceCodes(User user) {
        codes.deleteByUserId(user.getId());
        List<String> plaintext = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String code = generateCode();
            plaintext.add(code);
            codes.save(new MfaRecoveryCode(user, hash(code)));
        }
        return plaintext;
    }

    public boolean consume(User user, String presentedCode) {
        if (user == null || user.getId() == null || presentedCode == null) {
            return false;
        }
        String normalized = presentedCode.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if (normalized.length() != 10) {
            return false;
        }
        boolean consumed = codes.consume(user.getId(), hash(normalized), LocalDateTime.now()) == 1;
        if (consumed && metrics != null) {
            metrics.mfaRecoveryCodeConsumed();
        }
        return consumed;
    }

    public void revokeCodes(User user) {
        if (user != null && user.getId() != null) {
            codes.deleteByUserId(user.getId());
        }
    }

    private String generateCode() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) value.append('-');
            value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    public String hash(String code) {
        try {
            String normalized = code.trim().toUpperCase(Locale.ROOT).replace("-", "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
