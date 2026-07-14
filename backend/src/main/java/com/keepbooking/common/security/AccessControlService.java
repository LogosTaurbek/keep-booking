package com.keepbooking.common.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.keepbooking.restaurant.model.Restaurant;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserRole;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Single source of truth for the "can this account manage company/restaurant X" question
 * (tz2.txt role redesign): ROLE_SUPER_ADMIN can manage anything; ROLE_COMPANY_ADMIN can manage
 * every restaurant under its own companyId; ROLE_RESTAURANT_ADMIN can manage only the one
 * restaurant matching its own restaurantId. Scope lives directly on the User row (see
 * migration V020) rather than in a separate membership table.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;

    public boolean canManageCompany(Long actorId, Long companyId) {
        return userRepository.findById(actorId).map(actor -> canManageCompany(actor, companyId)).orElse(false);
    }

    public boolean canManageRestaurant(Long actorId, Restaurant restaurant) {
        return userRepository.findById(actorId).map(actor -> canManageRestaurant(actor, restaurant)).orElse(false);
    }

    public void verifyCanManageCompany(Long actorId, Long companyId) {
        if (!canManageCompany(actorId, companyId)) {
            throw new AccessDeniedException("You don't manage this company");
        }
    }

    public void verifyCanManageRestaurant(Long actorId, Restaurant restaurant) {
        if (!canManageRestaurant(actorId, restaurant)) {
            throw new AccessDeniedException("You don't manage this restaurant");
        }
    }

    private boolean canManageCompany(User actor, Long companyId) {
        if (actor.getRole() == UserRole.ROLE_SUPER_ADMIN) {
            return true;
        }
        return actor.getRole() == UserRole.ROLE_COMPANY_ADMIN
                && actor.getCompanyId() != null
                && actor.getCompanyId().equals(companyId);
    }

    private boolean canManageRestaurant(User actor, Restaurant restaurant) {
        if (actor.getRole() == UserRole.ROLE_SUPER_ADMIN) {
            return true;
        }
        if (actor.getCompanyId() == null || !actor.getCompanyId().equals(restaurant.getCompany().getId())) {
            return false;
        }
        if (actor.getRole() == UserRole.ROLE_COMPANY_ADMIN) {
            return true;
        }
        return actor.getRole() == UserRole.ROLE_RESTAURANT_ADMIN
                && actor.getRestaurantId() != null
                && actor.getRestaurantId().equals(restaurant.getId());
    }
}
