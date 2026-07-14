package com.keepbooking.restaurant.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.restaurant.model.RestaurantStatus;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    List<Restaurant> findByCompanyId(Long companyId);

    Optional<Restaurant> findByIdAndCompanyId(Long id, Long companyId);

    Page<Restaurant> findByStatus(RestaurantStatus status, Pageable pageable);

    long countByStatus(RestaurantStatus status);

    // earth_box(...) @> is an index-friendly bounding-cube pre-filter (cube/earthdistance extensions);
    // the exact earth_distance(...) <= check is still needed since earth_box approximates a circle with a box.
    @Query(
            value = """
                    SELECT r.* FROM restaurants r
                    WHERE r.status = 'ACTIVE'
                      AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
                      AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters) @> ll_to_earth(r.latitude, r.longitude)
                      AND earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(r.latitude, r.longitude)) <= :radiusMeters
                    ORDER BY earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(r.latitude, r.longitude))
                    """,
            countQuery = """
                    SELECT count(*) FROM restaurants r
                    WHERE r.status = 'ACTIVE'
                      AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
                      AND earth_box(ll_to_earth(:lat, :lng), :radiusMeters) @> ll_to_earth(r.latitude, r.longitude)
                      AND earth_distance(ll_to_earth(:lat, :lng), ll_to_earth(r.latitude, r.longitude)) <= :radiusMeters
                    """,
            nativeQuery = true)
    Page<Restaurant> findNearby(@Param("lat") double lat, @Param("lng") double lng,
                                 @Param("radiusMeters") double radiusMeters, Pageable pageable);

    // Plain range filter for a map viewport (JPQL BETWEEN excludes NULL lat/lng implicitly),
    // no earthdistance extension needed since a bounding box has no radius to approximate.
    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.status = 'ACTIVE'
              AND r.latitude BETWEEN :minLat AND :maxLat
              AND r.longitude BETWEEN :minLng AND :maxLng
            """)
    Page<Restaurant> findInBoundingBox(@Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
                                        @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng,
                                        Pageable pageable);
}
