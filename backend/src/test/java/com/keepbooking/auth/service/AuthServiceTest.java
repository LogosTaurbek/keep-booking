package com.keepbooking.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.keepbooking.auth.dto.ChangePasswordRequest;
import com.keepbooking.auth.dto.LoginRequest;
import com.keepbooking.auth.dto.RegisterRequest;
import com.keepbooking.auth.dto.TokenResponse;
import com.keepbooking.auth.model.RefreshToken;
import com.keepbooking.auth.model.TokenPurpose;
import com.keepbooking.auth.model.UserToken;
import com.keepbooking.auth.repository.RefreshTokenRepository;
import com.keepbooking.auth.repository.UserTokenRepository;
import com.keepbooking.auth.security.JwtTokenProvider;
import com.keepbooking.common.config.AppProperties;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserStatus;
import com.keepbooking.user.repository.UserRepository;

/**
 * Unit tests for the authorization critical path (tz2.txt §4 / §21): registration,
 * login, refresh-token rotation, email verification, password reset/change.
 * JwtTokenProvider is used as a real instance (cheap, deterministic) rather than a
 * mock, so token issuance is exercised for real; only persistence/collaborators are mocked.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserTokenRepository userTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setSecret("test-jwt-secret-key-must-be-at-least-32-chars-long!!");
        appProperties.getJwt().setAccessTokenExpirationMs(900_000);
        appProperties.getJwt().setRefreshTokenExpirationMs(2_592_000_000L);
        appProperties.getTokens().setEmailVerificationExpirationMs(86_400_000);
        appProperties.getTokens().setPasswordResetExpirationMs(3_600_000);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(appProperties);

        authService = new AuthService(userRepository, refreshTokenRepository, userTokenRepository,
                passwordEncoder, jwtTokenProvider, authenticationManager, appProperties);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstname("Test");
        request.setLastname("User");
        request.setEmail("test@example.com");
        request.setPassword("Password1!");
        return request;
    }

    private User existingUser() {
        return User.builder()
                .id(1L).firstname("Test").lastname("User").email("test@example.com")
                .passwordHash("hashed").status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerHashesPasswordSavesUserIssuesVerificationTokenAndAuthTokens() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("bcrypt-hash");

        TokenResponse response = authService.register(registerRequest());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getEmailVerified()).isFalse();

        ArgumentCaptor<UserToken> tokenCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);

        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginThrowsWhenUserNotFoundAfterAuthentication() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("whatever");
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void loginThrowsWhenUserIsBlocked() {
        User blocked = existingUser();
        blocked.setStatus(UserStatus.BLOCKED);
        LoginRequest request = new LoginRequest();
        request.setEmail(blocked.getEmail());
        request.setPassword("whatever");
        when(userRepository.findByEmailAndDeletedAtIsNull(blocked.getEmail())).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_BLOCKED);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginSucceedsAndIssuesTokensForActiveUser() {
        User user = existingUser();
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("Password1!");
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));

        TokenResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenNotFoundOrAlreadyRevoked() {
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("some-raw-token"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void refreshThrowsAndRevokesStoredTokenWhenExpired() {
        RefreshToken stored = RefreshToken.builder()
                .id(1L).user(existingUser()).tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(60)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);

        assertThat(stored.getRevoked()).isTrue();
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewOnesWhenValid() {
        RefreshToken stored = RefreshToken.builder()
                .id(1L).user(existingUser()).tokenHash("hash")
                .expiresAt(Instant.now().plusSeconds(60)).revoked(false).build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));

        TokenResponse response = authService.refresh("valid-token");

        assertThat(stored.getRevoked()).isTrue();
        assertThat(response.getAccessToken()).isNotBlank();
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void changePasswordThrowsWhenCurrentPasswordIsIncorrect() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("NewPassword1!");

        assertThatThrownBy(() -> authService.changePassword(user.getId(), request))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void changePasswordUpdatesHashAndRevokesAllRefreshTokensOnSuccess() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("correct");
        request.setNewPassword("NewPassword1!");

        authService.changePassword(user.getId(), request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }

    @Test
    void verifyEmailThrowsWhenTokenNotFoundOrAlreadyUsed() {
        when(userTokenRepository.findByTokenHashAndPurposeAndUsedAtIsNull(anyString(), eq(TokenPurpose.EMAIL_VERIFICATION)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bogus"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void verifyEmailThrowsWhenTokenExpired() {
        User user = existingUser();
        UserToken expired = UserToken.builder().id(1L).user(user).purpose(TokenPurpose.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(userTokenRepository.findByTokenHashAndPurposeAndUsedAtIsNull(anyString(), eq(TokenPurpose.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.verifyEmail("expired"))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void verifyEmailMarksEmailVerifiedOnValidToken() {
        User user = existingUser();
        user.setEmailVerified(false);
        UserToken valid = UserToken.builder().id(1L).user(user).purpose(TokenPurpose.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(userTokenRepository.findByTokenHashAndPurposeAndUsedAtIsNull(anyString(), eq(TokenPurpose.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(valid));

        authService.verifyEmail("valid-token");

        assertThat(user.getEmailVerified()).isTrue();
        assertThat(valid.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void forgotPasswordSilentlyNoOpsWhenEmailDoesNotExist() {
        when(userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("ghost@example.com");

        verify(userTokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordIssuesResetTokenWhenUserExists() {
        User user = existingUser();
        when(userRepository.findByEmailAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));

        authService.forgotPassword(user.getEmail());

        ArgumentCaptor<UserToken> captor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(TokenPurpose.PASSWORD_RESET);
    }

    @Test
    void resetPasswordUpdatesPasswordHashAndRevokesAllRefreshTokens() {
        User user = existingUser();
        UserToken valid = UserToken.builder().id(1L).user(user).purpose(TokenPurpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(userTokenRepository.findByTokenHashAndPurposeAndUsedAtIsNull(anyString(), eq(TokenPurpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("reset-hash");

        authService.resetPassword("valid-token", "NewPassword1!");

        assertThat(user.getPasswordHash()).isEqualTo("reset-hash");
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
    }
}
