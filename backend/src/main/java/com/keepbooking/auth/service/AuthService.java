package com.keepbooking.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.model.UserStatus;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserTokenRepository userTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .role(UserRole.ROLE_USER)
                .emailVerified(false)
                .build();

        userRepository.save(user);
        issueToken(user, TokenPurpose.EMAIL_VERIFICATION, appProperties.getTokens().getEmailVerificationExpirationMs());
        return issueTokens(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UserToken token = consumeToken(rawToken, TokenPurpose.EMAIL_VERIFICATION);
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        // Всегда отвечаем успехом, даже если email не найден — не раскрываем существование аккаунта
        userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user ->
                issueToken(user, TokenPurpose.PASSWORD_RESET, appProperties.getTokens().getPasswordResetExpirationMs()));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UserToken token = consumeToken(rawToken, TokenPurpose.PASSWORD_RESET);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ApiException(ErrorCode.USER_BLOCKED);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        stored.setRevoked(true);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plusMillis(appProperties.getJwt().getRefreshTokenExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.of(accessToken, rawRefreshToken,
                appProperties.getJwt().getAccessTokenExpirationMs());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void issueToken(User user, TokenPurpose purpose, long expirationMs) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        UserToken token = UserToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .purpose(purpose)
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .build();
        userTokenRepository.save(token);

        // TODO: подключить реальную отправку письма; пока логируем токен для локальной разработки
        log.info("[{}] token for {}: {}", purpose, user.getEmail(), rawToken);
    }

    private UserToken consumeToken(String rawToken, TokenPurpose purpose) {
        UserToken token = userTokenRepository.findByTokenHashAndPurposeAndUsedAtIsNull(hashToken(rawToken), purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        token.setUsedAt(Instant.now());
        return token;
    }
}
