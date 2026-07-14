package com.keepbooking.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.Company;
import com.keepbooking.restaurant.model.CompanyStatus;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    long countByStatus(CompanyStatus status);
}
