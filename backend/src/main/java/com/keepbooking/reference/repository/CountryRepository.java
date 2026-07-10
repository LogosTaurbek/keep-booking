package com.keepbooking.reference.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.reference.model.Country;

public interface CountryRepository extends JpaRepository<Country, Long> {

    Optional<Country> findByCode(String code);

    List<Country> findAllByOrderByNameAsc();
}
