package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByOwnerIdAndStatus(Long ownerId, CompanyStatus status);

    Optional<Company> findByIdAndOwnerId(Long id, Long ownerId);

    List<Company> findByOwnerId(Long ownerId);
}
