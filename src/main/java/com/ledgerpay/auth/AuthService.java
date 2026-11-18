package com.ledgerpay.auth;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.security.JwtService;
import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-not-used");
    }

    public RegisterResponse register(String email, String password) {
        String normalized = normalizeEmail(email);
        validatePassword(password);
        if (users.findByEmail(normalized).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        AppUser user = AppUser.register(normalized, passwordEncoder.encode(password), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        return new RegisterResponse(
                user.getId(), user.getEmail(), "Bearer", jwtService.issue(user), jwtService.expiresInSeconds());
    }

    public LoginResponse login(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByEmail(normalized).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = password != null && passwordEncoder.matches(password, hash);
        if (user == null || user.isSystemAccount() || !matches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }
        return new LoginResponse("Bearer", jwtService.issue(user), jwtService.expiresInSeconds());
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Email is required.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Email is not valid.");
        }
        return normalized;
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Password must be between 12 and 72 characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Password must be at most 72 bytes.");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR, "Password must contain at least one letter and one digit.");
        }
    }
}
