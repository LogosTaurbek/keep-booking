package com.keepbooking.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.admin.dto.SystemStatsDto;
import com.keepbooking.booking.model.BookingStatus;
import com.keepbooking.booking.repository.BookingRepository;
import com.keepbooking.restaurant.model.CompanyStatus;
import com.keepbooking.restaurant.model.RestaurantStatus;
import com.keepbooking.restaurant.repository.CompanyRepository;
import com.keepbooking.restaurant.repository.RestaurantRepository;
import com.keepbooking.user.model.UserStatus;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RestaurantRepository restaurantRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public SystemStatsDto getStats() {
        return SystemStatsDto.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByStatus(UserStatus.ACTIVE))
                .blockedUsers(userRepository.countByStatus(UserStatus.BLOCKED))
                .totalCompanies(companyRepository.count())
                .activeCompanies(companyRepository.countByStatus(CompanyStatus.ACTIVE))
                .pendingModerationCompanies(companyRepository.countByStatus(CompanyStatus.PENDING_MODERATION))
                .totalRestaurants(restaurantRepository.count())
                .activeRestaurants(restaurantRepository.countByStatus(RestaurantStatus.ACTIVE))
                .pendingModerationRestaurants(restaurantRepository.countByStatus(RestaurantStatus.PENDING_MODERATION))
                .totalBookings(bookingRepository.count())
                .completedBookings(bookingRepository.countByStatus(BookingStatus.COMPLETED))
                .cancelledBookings(bookingRepository.countByStatus(BookingStatus.CANCELLED))
                .build();
    }
}
