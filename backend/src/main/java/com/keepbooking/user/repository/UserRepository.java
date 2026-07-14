package com.keepbooking.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.model.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    List<User> findByCompanyIdAndRole(Long companyId, UserRole role);

    List<User> findByRestaurantIdAndRole(Long restaurantId, UserRole role);
}
