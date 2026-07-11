package com.keepbooking.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.keepbooking.common.dto.PageResponse;
import com.keepbooking.common.exception.ApiException;
import com.keepbooking.common.exception.ErrorCode;
import com.keepbooking.reference.repository.CityRepository;
import com.keepbooking.user.dto.UpdateProfileRequest;
import com.keepbooking.user.dto.UserProfileDto;
import com.keepbooking.user.mapper.UserMapper;
import com.keepbooking.user.model.User;
import com.keepbooking.user.model.UserStatus;
import com.keepbooking.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = findActive(userId);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActive(userId);

        if (request.getFirstname() != null) user.setFirstname(request.getFirstname());
        if (request.getLastname() != null) user.setLastname(request.getLastname());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getLanguage() != null) user.setLanguage(request.getLanguage());
        if (request.getTimezone() != null) user.setTimezone(request.getTimezone());

        if (request.getCityId() != null) {
            user.setCity(cityRepository.findById(request.getCityId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "City not found")));
        }

        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = findActive(userId);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(java.time.Instant.now());
        user.setEmail(user.getEmail() + ".deleted." + userId);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileDto> getAllUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return PageResponse.of(page.map(userMapper::toDto));
    }

    @Transactional
    public void setBlocked(Long userId, boolean blocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(blocked ? UserStatus.BLOCKED : UserStatus.ACTIVE);
        userRepository.save(user);
    }

    private User findActive(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
