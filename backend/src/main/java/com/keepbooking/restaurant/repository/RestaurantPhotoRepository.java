package com.keepbooking.restaurant.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.keepbooking.restaurant.model.RestaurantPhoto;

public interface RestaurantPhotoRepository extends JpaRepository<RestaurantPhoto, Long> {

    List<RestaurantPhoto> findByRestaurantIdOrderByPositionAsc(Long restaurantId);
}
