package com.ledgerpay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.security.JwtService;
import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository users;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encoded-password-hash");
        authService = new AuthService(users, passwordEncoder, jwtService, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registerStoresAHashAndNotThePassword() {
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.empty());
        when(jwtService.issue(any())).thenReturn("token");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        RegisterResponse response = authService.register("Ada@Example.com", "Password12345");

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$12$encoded-password-hash");
        assertThat(saved.getValue().getPasswordHash()).doesNotContain("Password12345");
        assertThat(response.accessToken()).isEqualTo("token");
        verify(passwordEncoder).encode("Password12345");
    }

    @Test
    void loginRejectsAWrongPasswordAndStillChecksTheHash() {
        AppUser user = AppUser.register("ada@example.com", "$2a$12$encoded-password-hash", Instant.parse("2026-01-01T00:00:00Z"));
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password1", "$2a$12$encoded-password-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ada@example.com", "wrong-password1"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(jwtService, never()).issue(any());
    }
}
