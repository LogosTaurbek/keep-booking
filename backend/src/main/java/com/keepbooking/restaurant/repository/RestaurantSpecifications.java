package com.keepbooking.restaurant.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.Specification;

import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;

public final class RestaurantSpecifications {

    private RestaurantSpecifications() {
    }

    public static Specification<Restaurant> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), RestaurantStatus.ACTIVE);
    }

    public static Specification<Restaurant> hasCityId(Long cityId) {
        return (root, query, cb) -> cityId == null ? null : cb.equal(root.get("city").get("id"), cityId);
    }

    public static Specification<Restaurant> nameContains(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Restaurant> hasCuisineSlug(String cuisineSlug) {
        return (root, query, cb) -> {
            if (cuisineSlug == null || cuisineSlug.isBlank()) {
                return null;
            }
            query.distinct(true);
            return cb.equal(root.join("cuisines").get("slug"), cuisineSlug);
        };
    }

    public static Specification<Restaurant> hasMinRating(BigDecimal minRating) {
        return (root, query, cb) -> minRating == null ? null : cb.greaterThanOrEqualTo(root.get("rating"), minRating);
    }
}
