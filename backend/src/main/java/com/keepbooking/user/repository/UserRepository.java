package com.keepbooking.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);
}
