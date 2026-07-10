package com.keepbooking.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.auth.model.TokenPurpose;
import com.keepbooking.auth.model.UserToken;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurposeAndUsedAtIsNull(String tokenHash, TokenPurpose purpose);
}
