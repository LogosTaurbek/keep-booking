package com.keepbooking.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);
}
