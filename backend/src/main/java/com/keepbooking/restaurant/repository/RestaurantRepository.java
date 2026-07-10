package com.keepbooking.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Page<Restaurant> findByCityIdAndStatus(Long cityId, RestaurantStatus status, Pageable pageable);

    Page<Restaurant> findByStatus(RestaurantStatus status, Pageable pageable);

    List<Restaurant> findByCompanyId(Long companyId);

    Optional<Restaurant> findByIdAndCompanyId(Long id, Long companyId);
}
