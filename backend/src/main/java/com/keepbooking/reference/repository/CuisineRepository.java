package com.keepbooking.reference.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.reference.model.Cuisine;

public interface CuisineRepository extends JpaRepository<Cuisine, Long> {

    List<Cuisine> findAllByOrderByNameAsc();
}
